package org.encinet.mik.module.geyser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeyserOptionalLinkageTest {

    private static final Path GEYSER_SOURCES = Path.of(
            "src/main/java/org/encinet/mik/module/geyser");

    @Test
    void integrationClassesRemainLoadableWithoutTheOptionalRuntimeApi() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.geysermc.geyser.api.GeyserApi"));
        assertDoesNotThrow(() -> Class.forName(GeyserService.class.getName()));
        assertDoesNotThrow(() -> Class.forName(
                "org.encinet.mik.module.menu.runtime.GeyserFloatingMenuPresenter"));
    }

    @Test
    void directApiIsUsedExceptAtTheCumulusClassLoaderBoundary() throws IOException {
        String adapter = Files.readString(GEYSER_SOURCES.resolve("GeyserApiAdapter.java"));
        String compat = Files.readString(
                GEYSER_SOURCES.resolve("CumulusFormCompatBridge.java"));

        assertTrue(adapter.contains("GeyserApi.api()"));
        assertTrue(adapter.contains("api.isBedrockPlayer(playerId)"));
        assertTrue(adapter.contains("api.connectionByUuid(playerId)"));
        assertFalse(adapter.contains("java.lang.reflect"));
        assertTrue(compat.contains("java.lang.reflect.Method"));

        try (var sources = Files.list(GEYSER_SOURCES)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (!source.getFileName().toString().equals("CumulusFormCompatBridge.java")) {
                    assertFalse(Files.readString(source).contains("java.lang.reflect"), source.toString());
                }
            }
        }
    }
}
