package org.encinet.mik.feature.community.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityBindingRepositorySchemaTest {
    @TempDir
    Path tempDirectory;

    @Test
    void freshDatabaseCreatesBindingAndCodeTables() throws Exception {
        Path database = tempDirectory.resolve("identity-bindings.db");
        IdentityBindingRepository repository = new IdentityBindingRepository(database.toFile());

        repository.open();
        repository.close();

        try (Connection connection = open(database);
             Statement statement = connection.createStatement()) {
            assertEquals(Set.of(
                            "binding_id", "player_uuid", "player_name", "platform", "issuer",
                            "identity_scope", "subject", "external_display_name", "scope_display_name",
                            "created_at", "verified_at"),
                    columns(statement, "identity_bindings"));
            assertEquals(Set.of(
                            "code_hash", "player_uuid", "player_name", "platform",
                            "created_at", "expires_at"),
                    columns(statement, "identity_link_codes"));
            assertEquals(Set.of("identity_bindings", "identity_link_codes"),
                    identityTables(statement));
        }
    }

    private static Connection open(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }

    private static Set<String> columns(Statement statement, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet results = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (results.next()) {
                columns.add(results.getString("name"));
            }
        }
        return columns;
    }

    private static Set<String> identityTables(Statement statement) throws SQLException {
        Set<String> tables = new LinkedHashSet<>();
        try (ResultSet results = statement.executeQuery("""
                SELECT name FROM sqlite_master
                WHERE type = 'table' AND name LIKE 'identity_%'
                """)) {
            while (results.next()) {
                tables.add(results.getString("name"));
            }
        }
        return tables;
    }
}
