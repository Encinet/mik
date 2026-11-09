package org.encinet.mik.feature.entertainment.music.jukebox;

import org.bukkit.block.Jukebox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class JukeboxPlaybackPreparationTest {
    @Test
    void digitalPlaybackClearsAnOccupiedSlotAndStopsItsPreviousSound() {
        var box = new Fixture(true);
        JukeboxPlaybackService.prepareJukebox(box.jukebox, true);
        assertFalse(box.playing);
        assertFalse(box.savedRecord);
    }

    @Test
    void digitalPlaybackAlsoWorksWithAnEmptySlot() {
        var box = new Fixture(false);
        JukeboxPlaybackService.prepareJukebox(box.jukebox, true);
        assertFalse(box.playing);
        assertFalse(box.savedRecord);
    }

    @Test
    void startingAnInsertedDiscPreservesThePhysicalItem() {
        var box = new Fixture(true);
        JukeboxPlaybackService.prepareJukebox(box.jukebox, false);
        assertFalse(box.playing);
        assertTrue(box.savedRecord);
    }

    private static final class Fixture {
        boolean playing = true;
        boolean record;
        boolean savedRecord;
        final Jukebox jukebox = (Jukebox) Proxy.newProxyInstance(Jukebox.class.getClassLoader(),
                new Class<?>[]{Jukebox.class}, (_, method, args) -> switch (method.getName()) {
                    case "hasRecord" -> record;
                    case "stopPlaying" -> { playing = false; yield null; }
                    case "setRecord" -> {
                        assertNull(args[0], "Digital playback must clear the slot without creating a replacement item");
                        record = false;
                        yield null;
                    }
                    case "update" -> { savedRecord = record; yield true; }
                    default -> throw new AssertionError(method.getName());
                });

        Fixture(boolean occupied) {
            record = occupied;
            savedRecord = occupied;
        }
    }
}
