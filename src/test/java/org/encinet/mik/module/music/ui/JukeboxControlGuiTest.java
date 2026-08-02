package org.encinet.mik.module.music.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JukeboxControlGuiTest {

    @Test
    void limitsEachSpatialQueuePageToTwelveTracks() {
        assertEquals(1, JukeboxControlGui.pageCount(0));
        assertEquals(1, JukeboxControlGui.pageCount(8));
        assertEquals(2, JukeboxControlGui.pageCount(9));
        assertEquals(4, JukeboxControlGui.pageCount(25));
    }

    @Test
    void clampsEmptyAndInvalidQueueSizesToOnePage() {
        assertEquals(1, JukeboxControlGui.pageCount(-1));
        assertEquals(1, JukeboxControlGui.pageCount(0));
    }
}
