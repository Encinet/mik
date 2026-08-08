package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmGameOverlayTest {

    @Test
    void everyJoiningPlayerGetsAStableThreeSecondCountdown() {
        long startedAt = 42_000_000_000L;
        RhythmGamePreRoll preRoll = new RhythmGamePreRoll(3_000L);
        preRoll.begin(startedAt);

        assertEquals(3, preRoll.numberAt(startedAt));
        assertEquals(3, preRoll.numberAt(startedAt + 999_000_000L));
        assertEquals(2, preRoll.numberAt(startedAt + 1_000_000_000L));
        assertEquals(1, preRoll.numberAt(startedAt + 2_000_000_000L));
        assertEquals(1, preRoll.numberAt(startedAt + 2_999_000_000L));
    }

    @Test
    void songTransportCannotStartBeforeCountdownFinishes() {
        long startedAt = 7_000_000_000L;
        RhythmGamePreRoll preRoll = new RhythmGamePreRoll(3_000L);
        AtomicInteger starts = new AtomicInteger();
        preRoll.begin(startedAt);

        assertFalse(preRoll.requestPlaybackStart(
                startedAt + 2_999_999_999L, starts::incrementAndGet));
        assertEquals(0, starts.get());

        assertTrue(preRoll.requestPlaybackStart(
                startedAt + 3_000_000_000L, starts::incrementAndGet));
        assertEquals(1, starts.get());
        assertFalse(preRoll.requestPlaybackStart(
                startedAt + 4_000_000_000L, starts::incrementAndGet));
        assertEquals(1, starts.get());
    }
}
