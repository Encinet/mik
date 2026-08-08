package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RhythmSpatialHudTest {
    @Test
    void hidesRecoveryArrowNearTheCrosshair() {
        assertNull(RhythmGameService.directionArrow(20.0, 10.0));
    }

    @Test
    void mapsOffscreenTargetsToEightStableDirections() {
        assertEquals("←", RhythmGameService.directionArrow(-50.0, 0.0));
        assertEquals("↗", RhythmGameService.directionArrow(50.0, -30.0));
        assertEquals("↓", RhythmGameService.directionArrow(0.0, 30.0));
    }

    @Test
    void exitHoldProgressIsVisibleAndBounded() {
        assertEquals("▱▱▱▱▱", RhythmGameService.exitProgressBar(-1.0));
        assertEquals("▰▰▰▱▱", RhythmGameService.exitProgressBar(0.5));
        assertEquals("▰▰▰▰▰", RhythmGameService.exitProgressBar(2.0));
    }

    @Test
    void depthHasANonColorMarker() {
        assertEquals("•", RhythmGameService.spatialDepthMarker(2.4));
        assertEquals("••", RhythmGameService.spatialDepthMarker(3.5));
        assertEquals("•••", RhythmGameService.spatialDepthMarker(4.6));
    }
}
