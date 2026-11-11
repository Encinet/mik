package org.encinet.mik.feature.community.identity;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class IdentityBindingRepository implements AutoCloseable {

    private static final String BINDING_SELECT = """
            SELECT binding_id, player_uuid, player_name, platform, issuer, identity_scope, subject,
                   external_display_name, scope_display_name, created_at, verified_at
            FROM identity_bindings
            """;

    private final File databaseFile;
    private Connection connection;

    IdentityBindingRepository(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    synchronized void open() throws SQLException {
        if (connection != null) {
            return;
        }
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create database directory " + parent);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException error) {
            throw new SQLException("SQLite JDBC driver is unavailable", error);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            createTables(statement);
            createIndexes(statement);
        } catch (SQLException error) {
            closeAfterOpenFailure(error);
        }
    }

    private void createTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS identity_bindings (
                    binding_id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    issuer TEXT NOT NULL,
                    identity_scope TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    external_display_name TEXT NOT NULL,
                    scope_display_name TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    verified_at INTEGER NOT NULL,
                    UNIQUE(platform, issuer, identity_scope, subject),
                    UNIQUE(player_uuid, platform, issuer, identity_scope)
                ) WITHOUT ROWID
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS identity_link_codes (
                    code_hash TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    UNIQUE(player_uuid, platform)
                )
                """);
    }

    private static void createIndexes(Statement statement) throws SQLException {
        statement.execute("CREATE INDEX IF NOT EXISTS identity_bindings_player "
                + "ON identity_bindings(player_uuid, platform, issuer, identity_scope)");
        statement.execute("CREATE INDEX IF NOT EXISTS identity_bindings_player_name "
                + "ON identity_bindings(player_name COLLATE NOCASE, player_uuid, platform)");
        statement.execute("CREATE INDEX IF NOT EXISTS identity_link_codes_expiry "
                + "ON identity_link_codes(expires_at)");
    }

    /** Reserve a globally unused code; collisions leave the player's current code untouched. */
    synchronized boolean tryReplaceCode(PendingIdentityLink pending) throws SQLException {
        ensureOpen();
        try (PreparedStatement cleanup = connection.prepareStatement(
                "DELETE FROM identity_link_codes WHERE expires_at <= ?");
             PreparedStatement upsert = connection.prepareStatement("""
                     INSERT INTO identity_link_codes(
                         code_hash, player_uuid, player_name, platform, created_at, expires_at
                     ) VALUES (?, ?, ?, ?, ?, ?)
                     ON CONFLICT(code_hash) DO NOTHING
                     ON CONFLICT(player_uuid, platform) DO UPDATE SET
                         code_hash = excluded.code_hash,
                         player_name = excluded.player_name,
                         created_at = excluded.created_at,
                         expires_at = excluded.expires_at
                     """)) {
            cleanup.setLong(1, pending.createdAt().toEpochMilli());
            cleanup.executeUpdate();
            upsert.setString(1, pending.codeHash());
            upsert.setString(2, pending.playerId().toString());
            upsert.setString(3, pending.playerName());
            upsert.setString(4, pending.platform());
            upsert.setLong(5, pending.createdAt().toEpochMilli());
            upsert.setLong(6, pending.expiresAt().toEpochMilli());
            return upsert.executeUpdate() == 1;
        }
    }

    synchronized int deleteExpiredCodes(Instant now) throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM identity_link_codes WHERE expires_at <= ?")) {
            statement.setLong(1, now.toEpochMilli());
            return statement.executeUpdate();
        }
    }

    synchronized boolean cancelCode(UUID playerId, String platform) throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM identity_link_codes WHERE player_uuid = ? AND platform = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, platform);
            return statement.executeUpdate() > 0;
        }
    }

    synchronized boolean codeIsPending(UUID playerId, String platform, String codeHash, Instant now) throws SQLException {
        ensureOpen();
        PendingIdentityLink pending = findPending(codeHash);
        return pending != null && pending.playerId().equals(playerId) && pending.platform().equals(platform)
                && now.isBefore(pending.expiresAt());
    }

    synchronized RepositoryLinkResult redeem(
            String codeHash,
            ExternalIdentity identity,
            Instant now
    ) throws SQLException {
        ensureOpen();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            PendingIdentityLink pending = findPending(codeHash);
            if (pending == null) {
                connection.rollback();
                return new RepositoryLinkResult(RepositoryLinkStatus.NOT_FOUND, null);
            }
            if (!now.isBefore(pending.expiresAt())) {
                deleteCode(codeHash);
                connection.commit();
                return new RepositoryLinkResult(RepositoryLinkStatus.EXPIRED, null);
            }
            ExternalIdentityKey key = identity.key();
            if (!pending.platform().equals(key.platform())) {
                connection.rollback();
                return new RepositoryLinkResult(RepositoryLinkStatus.PLATFORM_MISMATCH, null);
            }

            IdentityBinding externalBinding = findByExternalInternal(key);
            if (externalBinding != null) {
                if (!externalBinding.playerId().equals(pending.playerId())) {
                    connection.rollback();
                    return new RepositoryLinkResult(
                            RepositoryLinkStatus.EXTERNAL_IDENTITY_IN_USE, externalBinding);
                }
                updateVerification(externalBinding.externalKey(), pending.playerName(),
                        identity.displayName(), identity.scopeDisplayName(), now);
                deleteCode(codeHash);
                IdentityBinding refreshed = findByExternalInternal(externalBinding.externalKey());
                connection.commit();
                return new RepositoryLinkResult(RepositoryLinkStatus.ALREADY_LINKED, refreshed);
            }

            IdentityBinding occupiedScope = findPlayerScope(
                    pending.playerId(), key.platform(), key.issuer(), key.scope());
            if (occupiedScope != null) {
                connection.rollback();
                return new RepositoryLinkResult(
                        RepositoryLinkStatus.PLAYER_SCOPE_IN_USE, occupiedScope);
            }

            IdentityBinding inserted = insertBinding(pending, identity, now);
            deleteCode(codeHash);
            connection.commit();
            return new RepositoryLinkResult(RepositoryLinkStatus.LINKED, inserted);
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    synchronized Optional<IdentityBinding> findByExternal(ExternalIdentityKey key)
            throws SQLException {
        ensureOpen();
        return Optional.ofNullable(findByExternalInternal(key));
    }

    synchronized List<IdentityBinding> findByPlayer(UUID playerId) throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(BINDING_SELECT + """
                WHERE player_uuid = ?
                ORDER BY platform, issuer, identity_scope, subject
                """)) {
            statement.setString(1, playerId.toString());
            return readBindings(statement);
        }
    }

    synchronized List<IdentityBinding> findAll() throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(BINDING_SELECT + """
                ORDER BY player_uuid, platform, issuer, identity_scope, subject
                """)) {
            return readBindings(statement);
        }
    }

    synchronized List<IdentityBinding> findByPlayerName(String playerName) throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(BINDING_SELECT + """
                WHERE player_name = ? COLLATE NOCASE
                ORDER BY player_uuid, platform, issuer, identity_scope, subject
                """)) {
            statement.setString(1, playerName);
            return readBindings(statement);
        }
    }

    synchronized Optional<IdentityBinding> unlinkExternal(
            ExternalIdentityKey key
    ) throws SQLException {
        ensureOpen();
        IdentityBinding binding = findByExternalInternal(key);
        return unlink(binding);
    }

    synchronized Optional<IdentityBinding> unlinkOwned(UUID playerId, UUID bindingId) throws SQLException {
        ensureOpen();
        IdentityBinding binding = findById(bindingId);
        if (binding == null || !binding.playerId().equals(playerId)) return Optional.empty();
        return unlink(binding);
    }

    synchronized Optional<IdentityBinding> unlinkById(UUID bindingId) throws SQLException {
        ensureOpen();
        return unlink(findById(bindingId));
    }

    private IdentityBinding findById(UUID bindingId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                BINDING_SELECT + "WHERE binding_id = ?")) {
            statement.setString(1, bindingId.toString());
            return readSingleBinding(statement);
        }
    }

    synchronized void updatePlayerName(UUID playerId, String playerName) throws SQLException {
        ensureOpen();
        try (PreparedStatement bindings = connection.prepareStatement("""
                UPDATE identity_bindings SET player_name = ? WHERE player_uuid = ?
                """); PreparedStatement codes = connection.prepareStatement("""
                UPDATE identity_link_codes SET player_name = ? WHERE player_uuid = ?
                """)) {
            bindings.setString(1, playerName);
            bindings.setString(2, playerId.toString());
            bindings.executeUpdate();
            codes.setString(1, playerName);
            codes.setString(2, playerId.toString());
            codes.executeUpdate();
        }
    }

    private Optional<IdentityBinding> unlink(IdentityBinding binding) throws SQLException {
        if (binding == null) {
            return Optional.empty();
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (!deleteBinding(binding)) {
                connection.rollback();
                return Optional.empty();
            }
            connection.commit();
            return Optional.of(binding);
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private boolean deleteBinding(IdentityBinding binding) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM identity_bindings WHERE binding_id = ? AND player_uuid = ?
                """)) {
            statement.setString(1, binding.bindingId().toString());
            statement.setString(2, binding.playerId().toString());
            return statement.executeUpdate() == 1;
        }
    }

    private PendingIdentityLink findPending(String codeHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT code_hash, player_uuid, player_name, platform, created_at, expires_at
                FROM identity_link_codes WHERE code_hash = ?
                """)) {
            statement.setString(1, codeHash);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                return new PendingIdentityLink(
                        results.getString("code_hash"),
                        UUID.fromString(results.getString("player_uuid")),
                        results.getString("player_name"),
                        results.getString("platform"),
                        Instant.ofEpochMilli(results.getLong("created_at")),
                        Instant.ofEpochMilli(results.getLong("expires_at")));
            }
        }
    }

    private IdentityBinding findByExternalInternal(ExternalIdentityKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(BINDING_SELECT + """
                WHERE platform = ? AND issuer = ? AND identity_scope = ? AND subject = ?
                """)) {
            bindExternalKey(statement, key, 1);
            return readSingleBinding(statement);
        }
    }

    private IdentityBinding findPlayerScope(
            UUID playerId,
            String platform,
            String issuer,
            String scope
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(BINDING_SELECT + """
                WHERE player_uuid = ? AND platform = ? AND issuer = ? AND identity_scope = ?
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, platform);
            statement.setString(3, issuer);
            statement.setString(4, scope);
            return readSingleBinding(statement);
        }
    }

    private IdentityBinding insertBinding(
            PendingIdentityLink pending,
            ExternalIdentity identity,
            Instant now
    ) throws SQLException {
        ExternalIdentityKey key = identity.key();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO identity_bindings(
                    binding_id, player_uuid, player_name, platform, issuer, identity_scope, subject,
                    external_display_name, scope_display_name, created_at, verified_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, pending.playerId().toString());
            statement.setString(3, pending.playerName());
            statement.setString(4, key.platform());
            statement.setString(5, key.issuer());
            statement.setString(6, key.scope());
            statement.setString(7, key.subject());
            statement.setString(8, identity.displayName());
            statement.setString(9, identity.scopeDisplayName());
            statement.setLong(10, now.toEpochMilli());
            statement.setLong(11, now.toEpochMilli());
            statement.executeUpdate();
            IdentityBinding binding = findByExternalInternal(key);
            if (binding == null) {
                throw new SQLException("Inserted identity binding could not be reloaded");
            }
            return binding;
        }
    }

    private void updateVerification(
            ExternalIdentityKey key,
            String playerName,
            String externalDisplayName,
            String scopeDisplayName,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE identity_bindings
                SET player_name = ?, external_display_name = ?, scope_display_name = ?, verified_at = ?
                WHERE platform = ? AND issuer = ? AND identity_scope = ? AND subject = ?
                """)) {
            statement.setString(1, playerName);
            statement.setString(2, externalDisplayName);
            statement.setString(3, scopeDisplayName);
            statement.setLong(4, now.toEpochMilli());
            bindExternalKey(statement, key, 5);
            statement.executeUpdate();
        }
    }

    private void deleteCode(String codeHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM identity_link_codes WHERE code_hash = ?")) {
            statement.setString(1, codeHash);
            statement.executeUpdate();
        }
    }

    private static void bindExternalKey(
            PreparedStatement statement,
            ExternalIdentityKey key,
            int firstIndex
    ) throws SQLException {
        statement.setString(firstIndex, key.platform());
        statement.setString(firstIndex + 1, key.issuer());
        statement.setString(firstIndex + 2, key.scope());
        statement.setString(firstIndex + 3, key.subject());
    }

    private static List<IdentityBinding> readBindings(PreparedStatement statement)
            throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            List<IdentityBinding> bindings = new ArrayList<>();
            while (results.next()) {
                bindings.add(readBinding(results));
            }
            return List.copyOf(bindings);
        }
    }

    private static IdentityBinding readSingleBinding(PreparedStatement statement)
            throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            return results.next() ? readBinding(results) : null;
        }
    }

    private static IdentityBinding readBinding(ResultSet results) throws SQLException {
        return new IdentityBinding(
                UUID.fromString(results.getString("binding_id")),
                UUID.fromString(results.getString("player_uuid")),
                results.getString("player_name"),
                new ExternalIdentityKey(
                        results.getString("platform"),
                        results.getString("issuer"),
                        results.getString("identity_scope"),
                        results.getString("subject")),
                results.getString("external_display_name"),
                results.getString("scope_display_name"),
                Instant.ofEpochMilli(results.getLong("created_at")),
                Instant.ofEpochMilli(results.getLong("verified_at")));
    }

    private void ensureOpen() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Identity database is not open");
        }
    }

    private void closeAfterOpenFailure(SQLException cause) throws SQLException {
        try {
            close();
        } catch (SQLException closeFailure) {
            cause.addSuppressed(closeFailure);
        }
        throw cause;
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    record PendingIdentityLink(
            String codeHash,
            UUID playerId,
            String playerName,
            String platform,
            Instant createdAt,
            Instant expiresAt
    ) {
    }

    record RepositoryLinkResult(RepositoryLinkStatus status, IdentityBinding binding) {
    }

    enum RepositoryLinkStatus {
        LINKED,
        ALREADY_LINKED,
        NOT_FOUND,
        EXPIRED,
        PLATFORM_MISMATCH,
        EXTERNAL_IDENTITY_IN_USE,
        PLAYER_SCOPE_IN_USE
    }
}
