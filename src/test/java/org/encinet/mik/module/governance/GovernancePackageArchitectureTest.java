package org.encinet.mik.module.governance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernancePackageArchitectureTest {
    private static final Path GOVERNANCE =
            Path.of("src/main/java/org/encinet/mik/module/governance");

    @Test
    void moduleIsOrganizedByCapabilityBeforeTechnology() {
        for (String capability : List.of(
                "membership", "voting", "removal", "delivery",
                "persistence/sqlite", "platform/paper/command",
                "platform/paper/delivery", "platform/paper/membership")) {
            assertTrue(Files.isDirectory(GOVERNANCE.resolve(capability)), capability);
        }
        assertFalse(Files.exists(GOVERNANCE.resolve("domain")));
        assertFalse(Files.exists(GOVERNANCE.resolve("application")));
        assertFalse(Files.exists(GOVERNANCE.resolve("adapter")));
    }

    @Test
    void rootContainsOnlyCompositionAndModuleWideFailures() throws IOException {
        try (Stream<Path> files = Files.list(GOVERNANCE)) {
            assertEquals(List.of(
                    "GovernanceException.java",
                    "GovernanceModule.java",
                    "GovernanceRepositoryException.java"),
                    files.filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .toList());
        }
    }

    @Test
    void globalGodServicesAndPortsDoNotReturn() {
        assertFalse(Files.exists(GOVERNANCE.resolve("application/GovernanceService.java")));
        assertFalse(Files.exists(GOVERNANCE.resolve(
                "application/port/GovernanceRepository.java")));
        assertFalse(Files.exists(GOVERNANCE.resolve(
                "membership/MembershipPolicy.java")));
        assertTrue(Files.isRegularFile(GOVERNANCE.resolve(
                "membership/MembershipService.java")));
        assertTrue(Files.isRegularFile(GOVERNANCE.resolve(
                "voting/VotingService.java")));
        assertTrue(Files.isRegularFile(GOVERNANCE.resolve(
                "removal/RemovalService.java")));
        assertTrue(Files.isRegularFile(GOVERNANCE.resolve(
                "delivery/DeliveryService.java")));
    }

    @Test
    void capabilityCoresAreIndependentOfPaperAndSqlite() throws IOException {
        for (String capability : List.of("membership", "voting", "removal", "delivery")) {
            assertSourcesDoNotContain(GOVERNANCE.resolve(capability),
                    "org.bukkit", "net.luckperms", "java.sql",
                    "module.governance.platform", "module.governance.persistence.sqlite");
        }
    }

    @Test
    void capabilityDependenciesPointTowardStablePrerequisites() throws IOException {
        assertSourcesDoNotContain(GOVERNANCE.resolve("membership"),
                "module.governance.voting", "module.governance.removal",
                "module.governance.delivery");
        assertSourcesDoNotContain(GOVERNANCE.resolve("voting"),
                "module.governance.removal", "module.governance.delivery");
        assertSourcesDoNotContain(GOVERNANCE.resolve("removal"),
                "module.governance.delivery");
        assertSourcesDoNotContain(GOVERNANCE.resolve("delivery"),
                "module.governance.membership", "module.governance.removal");
    }

    @Test
    void eachCapabilityOwnsANarrowPersistencePort() throws IOException {
        assertServiceUsesPort("membership", "Membership");
        assertServiceUsesPort("voting", "Voting");
        assertServiceUsesPort("removal", "Removal");
        assertServiceUsesPort("delivery", "Delivery");

        String sqlite = Files.readString(GOVERNANCE.resolve(
                "persistence/sqlite/SqliteGovernanceRepository.java"));
        for (String port : List.of(
                "MembershipRepository", "VotingRepository",
                "RemovalRepository", "DeliveryRepository")) {
            assertTrue(sqlite.contains(port), "SQLite must implement " + port);
        }
    }

    @Test
    void inboundAndOutboundAdaptersRemainIndependent() throws IOException {
        assertSourcesDoNotContain(GOVERNANCE.resolve("platform"),
                "java.sql", "module.governance.persistence.sqlite");
        assertSourcesDoNotContain(GOVERNANCE.resolve("persistence"),
                "org.bukkit", "net.luckperms", "module.governance.platform");
    }

    @Test
    void formerRuntimeGodObjectsStayAsSmallCoordinators() throws IOException {
        assertAtMostLines(GOVERNANCE.resolve("GovernanceModule.java"), 200);
        assertAtMostLines(GOVERNANCE.resolve(
                "platform/paper/command/GovernanceCommandController.java"), 220);
        assertAtMostLines(GOVERNANCE.resolve(
                "persistence/sqlite/SqliteGovernanceStore.java"), 350);
    }

    @Test
    void sqliteResponsibilitiesAreExplicitlySeparated() {
        for (String file : List.of(
                "SqliteGovernanceSchema.java",
                "SqliteGovernanceSchemaDefinition.java",
                "SqliteGovernanceRows.java",
                "SqliteMembershipStore.java",
                "SqliteVotingStore.java",
                "SqliteDeliveryStore.java")) {
            assertTrue(Files.isRegularFile(
                    GOVERNANCE.resolve("persistence/sqlite").resolve(file)), file);
        }
    }

    private static void assertServiceUsesPort(String capability, String name)
            throws IOException {
        Path root = GOVERNANCE.resolve(capability);
        String service = Files.readString(root.resolve(name + "Service.java"));
        assertTrue(service.contains("private final " + name + "Repository repository;"),
                name + "Service must depend on its own repository port");
        assertFalse(service.contains("Sqlite"));
    }

    private static void assertAtMostLines(Path file, int maximum) throws IOException {
        try (Stream<String> lines = Files.lines(file)) {
            assertTrue(lines.count() <= maximum,
                    file + " must stay below " + maximum + " lines");
        }
    }

    private static void assertSourcesDoNotContain(Path root, String... forbidden)
            throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(path);
                for (String dependency : forbidden) {
                    assertFalse(source.contains(dependency),
                            path + " must not depend on " + dependency);
                }
            }
        }
    }
}
