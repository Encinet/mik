package org.encinet.mik.module.social.platform.matrix;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixPackageArchitectureTest {
    private static final Path MATRIX = Path.of(
            "src/main/java/org/encinet/mik/module/social/platform/matrix");

    @Test
    void adapterContainsOnlyProtocolCapabilities() throws IOException {
        assertTrue(Files.exists(MATRIX.resolve("client")));
        assertTrue(Files.exists(MATRIX.resolve("sync")));
        assertTrue(Files.exists(MATRIX.resolve("render")));
        assertFalse(Files.exists(MATRIX.resolve("command")));
        assertFalse(Files.exists(MATRIX.resolve("runtime")));
        assertFalse(Files.exists(MATRIX.resolve("safety")));

        try (Stream<Path> paths = Files.walk(MATRIX)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("IdentityBindingManager"), path.toString());
                assertFalse(source.contains("Bukkit.getScheduler"), path.toString());
                assertFalse(source.contains("com.sun.net.httpserver"), path.toString());
                assertFalse(source.contains("module.social.platform.qq"), path.toString());
            }
        }
    }

    @Test
    void compositionRootInstallsMatrixAlongsideQq() throws IOException {
        String module = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/social/SocialModule.java"));

        assertTrue(module.contains("new MatrixPlatformAdapter(plugin)"));
        assertTrue(module.contains("new QqPlatformAdapter(plugin)"));
        assertTrue(Files.exists(Path.of("src/main/resources/social/matrix.yml")));
    }
}
