package org.encinet.mik.module.identity;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityBindingRepositorySchemaTest {
    @TempDir
    Path tempDirectory;

    @Test
    void freshDatabaseUsesTheOnlySupportedFormat() throws Exception {
        Path database = tempDirectory.resolve("identity-bindings.db");
        IdentityBindingRepository repository = new IdentityBindingRepository(database.toFile());

        repository.open();
        repository.close();

        try (Connection connection = open(database);
             Statement statement = connection.createStatement()) {
            assertEquals(Set.of(
                            "player_uuid", "player_name", "platform", "issuer",
                            "identity_scope", "subject", "external_display_name",
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

    @Test
    void differentExistingFormatIsRejectedWithoutMigration() throws Exception {
        Path database = tempDirectory.resolve("identity-bindings.db");
        try (Connection connection = open(database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE identity_bindings (binding_id INTEGER PRIMARY KEY)");
        }

        IdentityBindingRepository repository = new IdentityBindingRepository(database.toFile());
        SQLException error = assertThrows(SQLException.class, repository::open);

        assertTrue(error.getMessage().contains("Unsupported identity database format"));
        try (Connection connection = open(database);
             Statement statement = connection.createStatement()) {
            assertEquals(Set.of("binding_id"), columns(statement, "identity_bindings"));
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
