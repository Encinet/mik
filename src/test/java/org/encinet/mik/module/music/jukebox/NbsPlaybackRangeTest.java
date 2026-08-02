package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NbsPlaybackRangeTest {

    @Test
    void preservesTheRealSourceInsideMinecraftNativeAttenuation() {
        Location source = new Location(null, 32.0, 65.0, 0.0);

        assertSame(source, NbsPlaybackRange.forListener(
                source, new Location(null, 0.0, 65.0, 0.0), 64, 4.0F));
    }

    @Test
    void compressesOnlyThePerceivedDistanceForTheExtendedRange() {
        Location audible = NbsPlaybackRange.forListener(
                new Location(null, 256.0, 65.0, 0.0),
                new Location(null, 0.0, 65.0, 0.0), 256, 4.0F);

        assertEquals(64.0, audible.getX(), 0.000_001);
        assertEquals(65.0, audible.getY(), 0.000_001);
        assertEquals(0.25, NbsPlaybackRange.spatialScale(256, 4.0F), 0.000_001);
    }
}
