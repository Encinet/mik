package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NbsPlaybackRangeTest {

    @Test
    void preservesTheRealSourceForANativeRange() {
        Location source = new Location(null, 12.0, 65.0, 0.0);

        assertSame(source, NbsPlaybackRange.forListener(
                source, new Location(null, 0.0, 65.0, 0.0), 16));
    }

    @Test
    void compressesOnlyThePerceivedDistanceForTheExtendedRange() {
        Location audible = NbsPlaybackRange.forListener(
                new Location(null, 256.0, 65.0, 0.0),
                new Location(null, 0.0, 65.0, 0.0), 256);

        assertEquals(16.0, audible.getX(), 0.000_001);
        assertEquals(65.0, audible.getY(), 0.000_001);
        assertEquals(0.0625, NbsPlaybackRange.spatialScale(256), 0.000_001);
    }
}
