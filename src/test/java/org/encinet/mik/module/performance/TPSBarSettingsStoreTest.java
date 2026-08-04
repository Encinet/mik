package org.encinet.mik.module.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TPSBarSettingsStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void visibilityChoiceSurvivesStoreReloads() throws Exception {
        File file = temporaryDirectory.resolve("tpsbar-settings.yml").toFile();
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TPSBarSettingsStore first = loaded(file);

        assertFalse(first.isEnabled(playerId));
        first.setEnabled(playerId, true);
        assertTrue(loaded(file).isEnabled(playerId));

        loaded(file).setEnabled(playerId, false);
        assertFalse(loaded(file).isEnabled(playerId));
    }

    @Test
    void storesEachPlayersChoiceIndependently() throws Exception {
        File file = temporaryDirectory.resolve("tpsbar-settings.yml").toFile();
        UUID enabled = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID hidden = UUID.fromString("22222222-2222-2222-2222-222222222222");
        TPSBarSettingsStore store = loaded(file);

        store.setEnabled(enabled, true);
        store.setEnabled(hidden, false);

        TPSBarSettingsStore reloaded = loaded(file);
        assertTrue(reloaded.isEnabled(enabled));
        assertFalse(reloaded.isEnabled(hidden));
    }

    private static TPSBarSettingsStore loaded(File file) {
        TPSBarSettingsStore store = new TPSBarSettingsStore(file);
        store.load();
        return store;
    }
}
