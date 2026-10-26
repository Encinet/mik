package org.encinet.mik.feature.player.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ClientPackageArchitectureTest {
    @Test
    void detectionAndItsConsumersDependOnThePublicCapabilities() throws Exception {
        Path root = Path.of("src/main/java/org/encinet/mik/feature/player/client");
        Pattern imports = Pattern.compile("(?m)^import (?:static )?([\\w.*]+);");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Path relative = root.relativize(file);
                boolean detection = relative.startsWith("detection");
                boolean tip = relative.startsWith("tip");
                for (var imported : imports.matcher(Files.readString(file)).results().map(match -> match.group(1)).toList()) {
                    if (!tip) {
                        assertFalse(imported.startsWith("org.encinet.mik.feature.player.guidance.")
                                        || imported.startsWith("org.encinet.mik.feature.player.client.tip.")
                                        || imported.startsWith("org.encinet.mik.shared.i18n.")
                                        || imported.startsWith("net.kyori.adventure."), file + " imports presentation: " + imported);
                    }
                    if (!detection) {
                        assertFalse(imported.startsWith("org.encinet.mik.feature.player.client.detection.")
                                        || imported.startsWith("com.viaversion.")
                                        || imported.startsWith("com.github.retrooper."), file + " imports a detector implementation: " + imported);
                    }
                }
            }
        }
    }
}
