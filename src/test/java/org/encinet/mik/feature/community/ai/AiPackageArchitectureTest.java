package org.encinet.mik.feature.community.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AiPackageArchitectureTest {
    private static final Path AI = Path.of("src/main/java/org/encinet/mik/feature/community/ai");

    @Test
    void publicGatewayHasNoBukkitSocialOrImplementationDependencies() throws IOException {
        try (Stream<Path> paths = Files.walk(AI.resolve("api"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("org.bukkit"), path.toString());
                assertFalse(source.contains("feature.community.social"), path.toString());
                assertFalse(source.contains("feature.community.ai.config"), path.toString());
                assertFalse(source.contains("feature.community.ai.conversation"), path.toString());
                assertFalse(source.contains("feature.community.ai.provider"), path.toString());
                assertFalse(source.contains("feature.community.ai.tool"), path.toString());
            }
        }
    }

    @Test
    void knowledgeModelAndApplicationDoNotDependOnBukkitOrSocialPlatforms()
            throws IOException {
        for (String layer : List.of("model", "application")) {
            try (Stream<Path> paths = Files.walk(AI.resolve("knowledge").resolve(layer))) {
                for (Path path : paths.filter(value -> value.toString()
                        .endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("org.bukkit"), path.toString());
                    assertFalse(source.contains("feature.community.social"), path.toString());
                }
            }
        }
    }
}
