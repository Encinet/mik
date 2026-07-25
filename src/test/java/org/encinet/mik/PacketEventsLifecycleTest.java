package org.encinet.mik;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketEventsLifecycleTest {

    private static final Path MAIN = Path.of("src/main/java/org/encinet/mik");

    @Test
    void externalPacketEventsPluginOwnsTheGlobalApiLifecycle() throws IOException {
        String production = allProductionSources();

        assertFalse(production.contains("PacketEvents.setAPI("));
        assertFalse(production.contains("PacketEvents.getAPI().load("));
        assertFalse(production.contains("PacketEvents.getAPI().init("));
        assertFalse(production.contains("PacketEvents.getAPI().terminate("));
        assertFalse(production.contains("SpigotPacketEventsBuilder"));
    }

    @Test
    void everyMikPacketListenerHasAnExplicitUnregisterPath() throws IOException {
        assertRegistersAndUnregisters("module/presentation/BrandingModule.java");
        assertRegistersAndUnregisters("module/player/GameModeSwitchModule.java");
        assertRegistersAndUnregisters("module/music/jukebox/VanillaRecordSilencer.java");

        String plugin = source("Mik.java");
        assertTrue(plugin.contains("gameModeSwitchModule.disable()"));
        assertTrue(plugin.contains("brandingModule.disable()"));
    }

    private static void assertRegistersAndUnregisters(String relative) throws IOException {
        String source = source(relative);
        assertTrue(source.contains("registerListener("), relative);
        assertTrue(source.contains("unregisterListener("), relative);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String allProductionSources() throws IOException {
        StringBuilder result = new StringBuilder();
        try (var files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }
}
