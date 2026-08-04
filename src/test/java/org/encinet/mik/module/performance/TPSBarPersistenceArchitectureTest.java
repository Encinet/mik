package org.encinet.mik.module.performance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TPSBarPersistenceArchitectureTest {

    @Test
    void modulePersistsTogglesAndRestoresEnabledPlayersOnJoin() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/performance/TPSBarModule.java"));

        assertTrue(source.contains("implements Listener"));
        assertTrue(source.contains("new File(plugin.getDataFolder(), \"tpsbar-settings.yml\")"));
        assertTrue(source.contains("settingsStore.setEnabled(playerId, enabled)"));
        assertTrue(source.contains("onPlayerJoin(PlayerJoinEvent event)"));
        assertTrue(source.contains("restoreTPSBar(event.getPlayer())"));
        assertTrue(source.contains("onPlayerQuit(PlayerQuitEvent event)"));
        assertTrue(source.contains("hideTPSBar(event.getPlayer())"));
    }
}
