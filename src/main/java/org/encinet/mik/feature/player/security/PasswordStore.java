package org.encinet.mik.feature.player.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the credential database. An absent hash is distinct from an unreadable database. */
final class PasswordStore implements AutoCloseable {
    record Removal(UUID account, UUID operation) { }
    private final Path path;
    private Connection connection;

    PasswordStore(Path path) { this.path = path; }

    synchronized Map<UUID, PasswordCredential> open() {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            boolean fresh = !Files.exists(path);
            if (Files.isSymbolicLink(path)) throw new SQLException("Credential database must not be a symbolic link");
            connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) { /* Non-POSIX host. */ }
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA synchronous=FULL");
                statement.execute("PRAGMA secure_delete=ON");
                if (fresh) {
                    statement.execute("""
                            CREATE TABLE credentials (
                                uuid TEXT PRIMARY KEY CHECK(length(uuid)=36),
                                hash TEXT,
                                updated_at INTEGER NOT NULL CHECK(updated_at>=0)
                            ) STRICT
                            """);
                    statement.execute("""
                            CREATE TABLE audit (
                                id INTEGER PRIMARY KEY,
                                target_uuid TEXT NOT NULL,
                                actor TEXT NOT NULL,
                                operation TEXT NOT NULL CHECK(operation IN ('SET','CHANGE','REMOVE','RESET')),
                                occurred_at INTEGER NOT NULL CHECK(occurred_at>=0)
                            ) STRICT
                            """);
                }
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS password_removals (
                            account TEXT PRIMARY KEY,
                            operation TEXT NOT NULL UNIQUE
                        ) STRICT
                        """);
                try (var result = statement.executeQuery("PRAGMA integrity_check")) {
                    if (!result.next() || !"ok".equals(result.getString(1)) || result.next()) {
                        throw new SQLException("Credential database integrity check failed");
                    }
                }
            }
            var result = new HashMap<UUID, PasswordCredential>();
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT uuid,hash,updated_at FROM credentials")) {
                while (rows.next()) {
                    UUID id = UUID.fromString(rows.getString(1));
                    if (!id.toString().equals(rows.getString(1))) throw new SQLException("Noncanonical account UUID");
                    result.put(id, new PasswordCredential(rows.getString(2), rows.getLong(3)));
                }
            }
            // Check the audit table too, before permitting any account changes.
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT target_uuid,actor,operation,occurred_at FROM audit")) {
                while (rows.next()) {
                    UUID.fromString(rows.getString(1));
                    if (!"console".equals(rows.getString(2))) UUID.fromString(rows.getString(2));
                    if (rows.getLong(4) < 0) throw new SQLException("Invalid audit timestamp");
                }
            }
            return Map.copyOf(result);
        } catch (Exception error) {
            close();
            throw new PasswordException(PasswordException.Code.STORAGE, error);
        }
    }

    synchronized PasswordCredential replace(UUID id, String hash, String actor, String operation) {
        try {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                PasswordCredential credential;
                try (var statement = connection.prepareStatement("""
                        INSERT INTO credentials(uuid,hash,updated_at) VALUES(?,?,?)
                        ON CONFLICT(uuid) DO UPDATE SET hash=excluded.hash,updated_at=max(credentials.updated_at+1,excluded.updated_at)
                        RETURNING updated_at
                        """)) {
                    statement.setString(1, id.toString()); statement.setString(2, hash); statement.setLong(3, now);
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Credential write did not affect one account");
                        credential = new PasswordCredential(hash, rows.getLong(1));
                    }
                }
                try (var statement = connection.prepareStatement(
                        "INSERT INTO audit(target_uuid,actor,operation,occurred_at) VALUES(?,?,?,?)")) {
                    statement.setString(1, id.toString()); statement.setString(2, actor);
                    statement.setString(3, operation); statement.setLong(4, now);
                    statement.executeUpdate();
                }
                connection.commit();
                return credential;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally { connection.setAutoCommit(true); }
        } catch (PasswordException error) { throw error; }
        catch (Exception error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
    }

    synchronized void beginRemoval(Removal removal) {
        try (var statement = connection.prepareStatement("INSERT INTO password_removals VALUES(?,?) ON CONFLICT(account) DO NOTHING")) {
            statement.setString(1, removal.account().toString());
            statement.setString(2, removal.operation().toString());
            if (statement.executeUpdate() == 0) throw new PasswordException(PasswordException.Code.BUSY);
        } catch (SQLException error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
    }

    synchronized java.util.List<Removal> removals() {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT account,operation FROM password_removals")) {
            var result = new java.util.ArrayList<Removal>();
            while (rows.next()) result.add(new Removal(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2))));
            return result;
        } catch (SQLException error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
    }

    synchronized void completeRemoval(Removal removal) {
        try (var statement = connection.prepareStatement("DELETE FROM password_removals WHERE account=? AND operation=?")) {
            statement.setString(1, removal.account().toString());
            statement.setString(2, removal.operation().toString());
            statement.executeUpdate();
        } catch (SQLException error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
    }

    @Override public synchronized void close() {
        if (connection == null) return;
        try { connection.close(); }
        catch (SQLException error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
        finally { connection = null; }
    }
}
