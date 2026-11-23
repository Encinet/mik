package org.encinet.mik.feature.entertainment.music.jukebox;

import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class JukeboxPlaybackPreparationTest {
    @Test
    void programPlaybackReplacesAnOccupiedSlotAndStopsItsPreviousSound() {
        var box = new Fixture(true);
        var replacement = mock(ItemStack.class);
        JukeboxPlaybackService.prepareJukebox(box.jukebox, true, replacement);
        assertFalse(box.playing);
        assertSame(replacement,box.savedRecord);
    }

    @Test
    void programPlaybackAlsoPlacesTheRequestedRecordInAnEmptySlot() {
        var box = new Fixture(false);
        var replacement = mock(ItemStack.class);
        JukeboxPlaybackService.prepareJukebox(box.jukebox, true, replacement);
        assertFalse(box.playing);
        assertSame(replacement,box.savedRecord);
    }

    @Test
    void startingAnInsertedDiscPreservesThePhysicalItem() {
        var box = new Fixture(true);
        var original = box.record;
        JukeboxPlaybackService.prepareJukebox(box.jukebox, false, null);
        assertFalse(box.playing);
        assertSame(original,box.savedRecord);
    }

    @Test
    void synchronizedPhysicalRecordFollowersKeepTheirSlotsEmpty() {
        var box = new Fixture(true);
        JukeboxPlaybackService.prepareJukebox(box.jukebox, true, null);
        assertFalse(box.playing);
        assertNull(box.savedRecord);
    }

    private static final class Fixture {
        boolean playing = true;
        ItemStack record;
        ItemStack savedRecord;
        final Jukebox jukebox = (Jukebox) Proxy.newProxyInstance(Jukebox.class.getClassLoader(),
                new Class<?>[]{Jukebox.class}, (_, method, args) -> switch (method.getName()) {
                    case "hasRecord" -> record != null;
                    case "stopPlaying" -> { playing = false; yield null; }
                    case "setRecord" -> {
                        record = (ItemStack) args[0];
                        yield null;
                    }
                    case "update" -> { savedRecord = record; yield true; }
                    default -> throw new AssertionError(method.getName());
                });

        Fixture(boolean occupied) {
            record = occupied ? mock(ItemStack.class) : null;
            savedRecord = record;
        }
    }
}
