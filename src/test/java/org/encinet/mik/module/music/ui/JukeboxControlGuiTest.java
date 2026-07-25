package org.encinet.mik.module.music.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JukeboxControlGuiTest {

    @Test
    void paginatesEveryTwentySevenQueueTracks() {
        assertEquals(1, JukeboxControlGui.pageCount(0));
        assertEquals(1, JukeboxControlGui.pageCount(27));
        assertEquals(2, JukeboxControlGui.pageCount(28));
        assertEquals(3, JukeboxControlGui.pageCount(55));
    }

    @Test
    void mapsOnlyQueueGridSlotsToPageOffsets() {
        assertEquals(0, JukeboxControlGui.queueSlotIndex(9));
        assertEquals(26, JukeboxControlGui.queueSlotIndex(35));
        assertEquals(-1, JukeboxControlGui.queueSlotIndex(8));
        assertEquals(-1, JukeboxControlGui.queueSlotIndex(
                JukeboxControlGui.NEXT_PAGE_SLOT));
    }
}
