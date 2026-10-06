package org.encinet.mik.module.music.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicBrowserGuiTest {

    @Test
    void browserShowsSixteenTracksPerSpatialPage() {
        assertEquals(1, MusicBrowserGui.pageCount(0));
        assertEquals(1, MusicBrowserGui.pageCount(16));
        assertEquals(2, MusicBrowserGui.pageCount(17));
        assertEquals(4, MusicBrowserGui.pageCount(60));
    }
}
