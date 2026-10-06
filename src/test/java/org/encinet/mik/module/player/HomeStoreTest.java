package org.encinet.mik.module.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeStoreTest {
    @TempDir Path directory;

    @Test
    void closeFlushesTheLastSnapshotAfterConsecutiveChanges() {
        Path file = directory.resolve("homes.yml");
        UUID playerId = UUID.randomUUID();
        try (HomeStore store = new HomeStore(file, Logger.getLogger("HomeStoreTest"))) {
            assertEquals(0, store.load());
            for (int index = 0; index < 40; index++) {
                store.put(playerId, "家", new HomeStore.Entry("world:" + index, null));
            }
            store.put(playerId, "矿洞", new HomeStore.Entry(
                    "world:128.5,64.0,-200.3,90.0,0.0", null));
            assertTrue(store.remove(playerId, "家"));
        }

        try (HomeStore reloaded = new HomeStore(file, Logger.getLogger("HomeStoreTest"))) {
            assertEquals(1, reloaded.load());
            assertNull(reloaded.get(playerId, "家"));
            assertEquals(new HomeStore.Entry(
                            "world:128.5,64.0,-200.3,90.0,0.0", null),
                    reloaded.get(playerId, "矿洞"));
        }
    }

    @Test
    void existingYamlKeysAndNamesWithDotsRemainReadable() throws Exception {
        Path file = directory.resolve("homes.yml");
        UUID playerId = UUID.randomUUID();
        Files.writeString(file, playerId + ":\n"
                + "  base: 'world:1,2,3,4,5'\n"
                + "  old:\n"
                + "    dotted: 'world:7,8,9,0,0'\n"
                + "invalid-player-id:\n"
                + "  ignored: 'world:0,0,0,0,0'\n");

        try (HomeStore store = new HomeStore(file, Logger.getLogger("HomeStoreTest"))) {
            assertEquals(2, store.load());
            assertEquals("world:1,2,3,4,5", store.get(playerId, "base").locationRaw());
            assertEquals("world:7,8,9,0,0", store.get(playerId, "old.dotted").locationRaw());
            store.put(playerId, "base.one", new HomeStore.Entry("world:5,4,3,2,1", null));
        }

        try (HomeStore reloaded = new HomeStore(file, Logger.getLogger("HomeStoreTest"))) {
            assertEquals(3, reloaded.load());
            assertTrue(reloaded.names(playerId).contains("base.one"));
            assertTrue(reloaded.names(playerId).contains("old.dotted"));
            assertEquals("world:5,4,3,2,1", reloaded.get(playerId, "base.one").locationRaw());
        }
    }

    @Test
    void malformedYamlIsNotSilentlyReplaced() throws Exception {
        Path file = directory.resolve("homes.yml");
        Files.writeString(file, "player: [unterminated\n");
        try (HomeStore store = new HomeStore(file, Logger.getLogger("HomeStoreTest"))) {
            assertThrows(IllegalStateException.class, store::load);
        }
        assertEquals("player: [unterminated\n", Files.readString(file));
    }

    @Test
    void failedFinalSnapshotIsReportedAtClose() throws Exception {
        Path file = directory.resolve("homes.yml");
        HomeStore store = new HomeStore(file, Logger.getLogger("HomeStoreTest"));
        store.load();
        Files.delete(file);
        Files.createDirectory(file);
        store.put(UUID.randomUUID(), "家", new HomeStore.Entry("world:1,2,3,4,5", null));

        assertThrows(IllegalStateException.class, store::close);
    }
}
