package org.encinet.mik.feature.community.governance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GovernancePackageArchitectureTest {
    private static final Path GOVERNANCE =
            Path.of("src/main/java/org/encinet/mik/feature/community/governance");

    @Test
    void capabilityCoresAreIndependentOfPaperAndSqlite() throws IOException {
        for (String capability : List.of("membership", "voting", "removal", "delivery")) {
            assertSourcesDoNotContain(GOVERNANCE.resolve(capability),
                    "org.bukkit", "net.luckperms", "java.sql",
                    "feature.community.governance.platform", "feature.community.governance.persistence.sqlite");
        }
    }

    @Test
    void capabilityDependenciesPointTowardStablePrerequisites() throws IOException {
        assertSourcesDoNotContain(GOVERNANCE.resolve("membership"),
                "feature.community.governance.voting", "feature.community.governance.removal",
                "feature.community.governance.delivery");
        assertSourcesDoNotContain(GOVERNANCE.resolve("voting"),
                "feature.community.governance.removal", "feature.community.governance.delivery");
        assertSourcesDoNotContain(GOVERNANCE.resolve("removal"),
                "feature.community.governance.delivery");
        assertSourcesDoNotContain(GOVERNANCE.resolve("delivery"),
                "feature.community.governance.membership", "feature.community.governance.removal");
    }

    @Test
    void inboundAndOutboundAdaptersRemainIndependent() throws IOException {
        assertSourcesDoNotContain(GOVERNANCE.resolve("platform"),
                "java.sql", "feature.community.governance.persistence.sqlite");
        assertSourcesDoNotContain(GOVERNANCE.resolve("persistence"),
                "org.bukkit", "net.luckperms", "feature.community.governance.platform");
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
