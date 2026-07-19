package org.encinet.mik.module.ban;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

final class BanRepository implements AutoCloseable {

    private static final int SCHEMA_VERSION = 1;

    private final File databaseFile;
    private Connection connection;

    BanRepository(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    void open() throws SQLException {
        if (connection != null) {
            return;
        }
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create database directory " + parent);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver is unavailable", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            int existingVersion;
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                existingVersion = version.next() ? version.getInt(1) : 0;
            }
            if (existingVersion > SCHEMA_VERSION) {
                throw new SQLException("Ban database schema " + existingVersion
                        + " is newer than supported schema " + SCHEMA_VERSION);
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT,
                        player_name TEXT NOT NULL,
                        normalized_name TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        source TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        expires_at INTEGER,
                        revoked_at INTEGER,
                        origin TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS bans_uuid_created ON bans(player_uuid, created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS bans_name_created ON bans(normalized_name, created_at DESC)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS paper_ban_state (
                        identity_key TEXT PRIMARY KEY,
                        player_uuid TEXT,
                        player_name TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        source TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ban_metadata (
                        metadata_key TEXT PRIMARY KEY,
                        metadata_value TEXT NOT NULL
                    )
                    """);
            statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
        } catch (SQLException e) {
            closeAfterOpenFailure(e);
        }
    }

    List<BanRecord> loadAll() throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, player_uuid, player_name, normalized_name, reason, source,
                       created_at, updated_at, expires_at, revoked_at, origin
                FROM bans
                ORDER BY created_at DESC, id DESC
                """); ResultSet results = statement.executeQuery()) {
            List<BanRecord> records = new ArrayList<>();
            while (results.next()) {
                records.add(readRecord(results));
            }
            return records;
        }
    }

    List<PaperBanSnapshot> loadPaperState() throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, player_name, reason, source, created_at, expires_at
                FROM paper_ban_state
                """); ResultSet results = statement.executeQuery()) {
            List<PaperBanSnapshot> snapshots = new ArrayList<>();
            while (results.next()) {
                String uuidText = results.getString("player_uuid");
                snapshots.add(new PaperBanSnapshot(
                        uuidText == null ? null : UUID.fromString(uuidText),
                        results.getString("player_name"),
                        results.getString("reason"),
                        results.getString("source"),
                        Instant.ofEpochMilli(results.getLong("created_at")),
                        nullableInstant(results, "expires_at")));
            }
            return snapshots;
        }
    }

    boolean hasPaperState() throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT metadata_value FROM ban_metadata WHERE metadata_key = 'paper-state-initialized'
                """ ); ResultSet results = statement.executeQuery()) {
            return results.next() && Boolean.parseBoolean(results.getString(1));
        }
    }

    void replacePaperState(Collection<PaperBanSnapshot> snapshots) throws SQLException {
        ensureOpen();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement delete = connection.createStatement();
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO paper_ban_state (
                         identity_key, player_uuid, player_name, reason, source, created_at, expires_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            delete.executeUpdate("DELETE FROM paper_ban_state");
            for (PaperBanSnapshot snapshot : snapshots) {
                insert.setString(1, snapshot.identityKey());
                setNullableUuid(insert, 2, snapshot.playerUuid());
                insert.setString(3, snapshot.playerName());
                insert.setString(4, snapshot.reason());
                insert.setString(5, snapshot.source());
                insert.setLong(6, snapshot.createdAt().toEpochMilli());
                setNullableInstant(insert, 7, snapshot.expiresAt());
                insert.addBatch();
            }
            insert.executeBatch();
            try (PreparedStatement metadata = connection.prepareStatement("""
                    INSERT INTO ban_metadata(metadata_key, metadata_value)
                    VALUES ('paper-state-initialized', 'true')
                    ON CONFLICT(metadata_key) DO UPDATE SET metadata_value = excluded.metadata_value
                    """)) {
                metadata.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    BanRecord insert(BanDraft draft) throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO bans (
                    player_uuid, player_name, normalized_name, reason, source,
                    created_at, updated_at, expires_at, revoked_at, origin
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            setNullableUuid(statement, 1, draft.playerUuid());
            statement.setString(2, draft.playerName());
            statement.setString(3, draft.normalizedName());
            statement.setString(4, draft.reason());
            statement.setString(5, draft.source());
            statement.setLong(6, draft.createdAt().toEpochMilli());
            statement.setLong(7, draft.createdAt().toEpochMilli());
            setNullableInstant(statement, 8, draft.expiresAt());
            statement.setString(9, draft.origin().name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return a ban record id");
                }
                return findById(keys.getLong(1));
            }
        }
    }

    BanRecord replace(long currentId, BanDraft replacement, Instant revokedAt) throws SQLException {
        ensureOpen();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            revoke(currentId, revokedAt);
            BanRecord inserted = insert(replacement);
            connection.commit();
            return inserted;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    BanRecord updateReason(long id, String reason, Instant updatedAt) throws SQLException {
        return update(id, "reason = ?", statement -> statement.setString(1, reason), updatedAt);
    }

    BanRecord updateExpiration(long id, Instant expiration, Instant updatedAt) throws SQLException {
        return update(id, "expires_at = ?", statement -> setNullableInstant(statement, 1, expiration), updatedAt);
    }

    BanRecord updateSeverity(long id, String reason, Instant expiration, Instant updatedAt) throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE bans
                SET reason = ?, expires_at = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, reason);
            setNullableInstant(statement, 2, expiration);
            statement.setLong(3, updatedAt.toEpochMilli());
            statement.setLong(4, id);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Ban record " + id + " does not exist");
            }
        }
        return findById(id);
    }

    BanRecord updateFromPaper(long id, BanDraft draft, Instant updatedAt) throws SQLException {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE bans
                SET player_uuid = ?, player_name = ?, normalized_name = ?, reason = ?, source = ?,
                    expires_at = ?, updated_at = ?
                WHERE id = ?
                """)) {
            setNullableUuid(statement, 1, draft.playerUuid());
            statement.setString(2, draft.playerName());
            statement.setString(3, draft.normalizedName());
            statement.setString(4, draft.reason());
            statement.setString(5, draft.source());
            setNullableInstant(statement, 6, draft.expiresAt());
            statement.setLong(7, updatedAt.toEpochMilli());
            statement.setLong(8, id);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Ban record " + id + " does not exist");
            }
        }
        return findById(id);
    }

    BanRecord revoke(long id, Instant revokedAt) throws SQLException {
        return update(id, "revoked_at = ?", statement -> statement.setLong(1, revokedAt.toEpochMilli()), revokedAt);
    }

    private BanRecord update(long id, String assignment, ParameterSetter setter, Instant updatedAt)
            throws SQLException {
        ensureOpen();
        String sql = "UPDATE bans SET " + assignment
                + ", updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setter.set(statement);
            statement.setLong(2, updatedAt.toEpochMilli());
            statement.setLong(3, id);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Ban record " + id + " does not exist");
            }
        }
        return findById(id);
    }

    private BanRecord findById(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, player_uuid, player_name, normalized_name, reason, source,
                       created_at, updated_at, expires_at, revoked_at, origin
                FROM bans WHERE id = ?
                """)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("Ban record " + id + " does not exist");
                }
                return readRecord(results);
            }
        }
    }

    private BanRecord readRecord(ResultSet results) throws SQLException {
        String uuidText = results.getString("player_uuid");
        return new BanRecord(
                results.getLong("id"),
                uuidText == null ? null : UUID.fromString(uuidText),
                results.getString("player_name"),
                results.getString("normalized_name"),
                results.getString("reason"),
                results.getString("source"),
                Instant.ofEpochMilli(results.getLong("created_at")),
                Instant.ofEpochMilli(results.getLong("updated_at")),
                nullableInstant(results, "expires_at"),
                nullableInstant(results, "revoked_at"),
                BanRecord.Origin.valueOf(results.getString("origin")));
    }

    private static Instant nullableInstant(ResultSet results, String column) throws SQLException {
        long value = results.getLong(column);
        return results.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static void setNullableInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value.toEpochMilli());
        }
    }

    private static void setNullableUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }

    private void ensureOpen() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Ban database is not open");
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
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    @FunctionalInterface
    private interface ParameterSetter {
        void set(PreparedStatement statement) throws SQLException;
    }
}
