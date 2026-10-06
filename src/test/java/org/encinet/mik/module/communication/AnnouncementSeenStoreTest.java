package org.encinet.mik.module.communication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnouncementSeenStoreTest {
    @TempDir Path directory;

    @Test
    void savesEachPlayersLatestSeenAnnouncement() {
        Path file = directory.resolve("announcements-state.yml");
        UUID returning = UUID.randomUUID();
        UUID newcomer = UUID.randomUUID();
        AnnouncementSeenStore store = new AnnouncementSeenStore(file,
                Logger.getLogger("AnnouncementSeenStoreTest"));
        store.load();
        assertEquals(100L, store.positionOrStartAt(returning, 100L));
        store.markSeenThrough(returning, 180L);
        store.markSeenThrough(returning, 120L);
        assertEquals(200L, store.positionOrStartAt(newcomer, 200L));
        store.save();

        AnnouncementSeenStore reloaded = new AnnouncementSeenStore(file,
                Logger.getLogger("AnnouncementSeenStoreTest"));
        reloaded.load();
        assertEquals(180L, reloaded.positionOrStartAt(returning, 300L));
        assertEquals(200L, reloaded.positionOrStartAt(newcomer, 300L));
    }

    @Test
    void invalidStateIsNotReplacedOnFailedLoad() throws Exception {
        Path file = directory.resolve("announcements-state.yml");
        String invalid = "players: [unterminated\n";
        Files.writeString(file, invalid);
        AnnouncementSeenStore store = new AnnouncementSeenStore(file,
                Logger.getLogger("AnnouncementSeenStoreTest"));

        assertThrows(IllegalStateException.class, store::load);
        store.save();
        assertEquals(invalid, Files.readString(file));
    }
}
