package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmMonotonicPlaybackClockTest {

    @Test
    void reconstructsPlaybackAtPacketArrivalAcrossBukkitTickDelay() {
        RhythmMonotonicPlaybackClock clock = new RhythmMonotonicPlaybackClock(
                2_000L, true, 1_000_000_000L);

        assertEquals(1_970L, clock.positionAt(970_000_000L));
        assertEquals(2_025L, clock.positionAt(1_025_000_000L));
    }

    @Test
    void pausedPlaybackDoesNotExtrapolate() {
        RhythmMonotonicPlaybackClock clock = new RhythmMonotonicPlaybackClock(
                2_000L, false, 1_000_000_000L);

        assertEquals(2_000L, clock.positionAt(800_000_000L));
        assertEquals(2_000L, clock.positionAt(1_200_000_000L));
    }
}
