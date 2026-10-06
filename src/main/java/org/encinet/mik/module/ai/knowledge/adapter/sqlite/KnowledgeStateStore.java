package org.encinet.mik.module.ai.knowledge.adapter.sqlite;

import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Small SQLite journal for maintenance status and metadata-only audit events. */
public final class KnowledgeStateStore implements AutoCloseable {
    private final Connection connection;

    public KnowledgeStateStore(Path pluginDataDirectory) {
        Path database = Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory")
                .toAbsolutePath().normalize().resolve("ai-knowledge.db");
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + database);
            initialize();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not open AI knowledge state", error);
        }
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS knowledge_state (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS knowledge_audit (
                        sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                        action TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        owner TEXT,
                        document_id TEXT NOT NULL,
                        revision INTEGER NOT NULL,
                        detail TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS knowledge_audit_document "
                    + "ON knowledge_audit(scope, owner, document_id, sequence)");
            statement.execute("PRAGMA user_version=1");
        }
    }

    public synchronized void state(String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_state(key, value) VALUES(?, ?)
                ON CONFLICT(key) DO UPDATE SET value=excluded.value
                """)) {
            statement.setString(1, key);
            statement.setString(2, Objects.requireNonNullElse(value, ""));
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not update AI knowledge state", error);
        }
    }

    public synchronized Optional<String> state(String key) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM knowledge_state WHERE key=?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read AI knowledge state", error);
        }
    }

    public synchronized void removeState(String key) {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM knowledge_state WHERE key=?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not remove AI knowledge state", error);
        }
    }

    public synchronized void audit(
            String action,
            KnowledgeDocument document,
            String detail
    ) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_audit(
                    action, scope, owner, document_id, revision, detail, created_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, action);
            statement.setString(2, document.scope().name().toLowerCase(java.util.Locale.ROOT));
            statement.setString(3, document.owner().map(Object::toString).orElse(null));
            statement.setString(4, document.id());
            statement.setLong(5, document.revision());
            String bounded = Objects.requireNonNullElse(detail, "").replace('\u0000', ' ');
            statement.setString(6, bounded.length() <= 500
                    ? bounded : bounded.substring(0, 500));
            statement.setString(7, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not audit AI knowledge change", error);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not close AI knowledge state", error);
        }
    }
}
