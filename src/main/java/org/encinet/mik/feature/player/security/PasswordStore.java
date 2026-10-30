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
                try (var result = statement.executeQuery("PRAGMA integrity_check")) {
                    if (!result.next() || !"ok".equals(result.getString(1)) || result.next()) {
                        throw new SQLException("Credential database integrity check failed");
                    }
                }
            }
            var result = new HashMap<UUID, PasswordCredential>();
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT uuid,hash FROM credentials")) {
                while (rows.next()) {
                    UUID id = UUID.fromString(rows.getString(1));
                    if (!id.toString().equals(rows.getString(1))) throw new SQLException("Noncanonical account UUID");
                    result.put(id, new PasswordCredential(rows.getString(2)));
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
        if (hash != null) PasswordHasher.validateEncoding(hash);
        PasswordCredential credential = new PasswordCredential(hash);
        try {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (var statement = connection.prepareStatement("""
                        INSERT INTO credentials(uuid,hash,updated_at) VALUES(?,?,?)
                        ON CONFLICT(uuid) DO UPDATE SET hash=excluded.hash,updated_at=excluded.updated_at
                        """)) {
                    statement.setString(1, id.toString()); statement.setString(2, hash); statement.setLong(3, now);
                    if (statement.executeUpdate() != 1) throw new SQLException("Credential write did not affect one account");
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

    @Override public synchronized void close() {
        if (connection == null) return;
        try { connection.close(); }
        catch (SQLException error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
        finally { connection = null; }
    }
}
