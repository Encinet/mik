package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NbsPlaybackPositionTest {

    @Test
    void centersNeutralPanningOnTheJukebox() {
        Location source = NbsPlaybackPosition.forListener(
                new Location(null, 10, 64, 20),
                new Location(null, 0, 65, 0, 0, 0), 0);

        assertEquals(10.5, source.getX(), 0.000_001);
        assertEquals(65.0, source.getY(), 0.000_001);
        assertEquals(20.5, source.getZ(), 0.000_001);
    }

    @Test
    void rotatesStereoWithEachListenerInsteadOfUsingWorldXAxis() {
        Location jukebox = new Location(null, 10, 64, 20);

        Location facingSouth = NbsPlaybackPosition.forListener(jukebox,
                new Location(null, 0, 65, 0, 0, 0), 100);
        Location facingEast = NbsPlaybackPosition.forListener(jukebox,
                new Location(null, 0, 65, 0, -90, 0), 100);

        assertEquals(12.5, facingSouth.getX(), 0.000_001);
        assertEquals(20.5, facingSouth.getZ(), 0.000_001);
        assertEquals(10.5, facingEast.getX(), 0.000_001);
        assertEquals(18.5, facingEast.getZ(), 0.000_001);
    }
}
