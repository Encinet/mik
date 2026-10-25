package org.encinet.mik.feature.community.social;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialPackageArchitectureTest {
    private static final Path SOCIAL = Path.of("src/main/java/org/encinet/mik/feature/community/social");

    @Test
    void sharedKernelAndFeaturesDoNotDependOnPlatformAdapters() throws IOException {
        for (String directory : List.of(
                "api", "chat", "command", "document", "game", "runtime", "safety")) {
            try (Stream<Path> paths = Files.walk(SOCIAL.resolve(directory))) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("feature.community.social.platform."), path.toString());
                }
            }
        }
    }

    @Test
    void runtimeDoesNotDependOnIdentityBindingImplementation() throws IOException {
        try (Stream<Path> paths = Files.walk(SOCIAL.resolve("runtime"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("IdentityBindingManager"), path.toString());
            }
        }
    }

    @Test
    void socialAiDependsOnlyOnThePublicAiGateway() throws IOException {
        String source = Files.readString(SOCIAL.resolve(
                "command/builtin/AiSocialCommand.java"));
        assertTrue(source.contains("feature.community.ai.api.AiGateway"));
        assertFalse(source.contains("feature.community.ai.config"));
        assertFalse(source.contains("feature.community.ai.conversation"));
        assertFalse(source.contains("feature.community.ai.provider"));
        assertFalse(source.contains("feature.community.ai.tool"));
    }

    @Test
    void gameBoundaryExposesDataWithoutDependingOnCommandsOrReplies()
            throws IOException {
        try (Stream<Path> paths = Files.walk(SOCIAL.resolve("game"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("feature.community.social.command"), path.toString());
                assertFalse(source.contains("feature.community.social.document"), path.toString());
                assertFalse(source.contains("feature.community.social.platform"), path.toString());
                assertFalse(source.contains("shared.i18n.Message"), path.toString());
            }
        }
    }
}
