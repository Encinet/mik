package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmGameOverlayTest {

    @Test
    void everyJoiningPlayerGetsAStableThreeSecondCountdown() {
        long joinedAt = 42_000L;
        long readyAt = joinedAt + 3_000L;

        assertEquals(3, RhythmGameService.countdownNumber(joinedAt, readyAt));
        assertEquals(3, RhythmGameService.countdownNumber(joinedAt + 999L, readyAt));
        assertEquals(2, RhythmGameService.countdownNumber(joinedAt + 1_000L, readyAt));
        assertEquals(1, RhythmGameService.countdownNumber(joinedAt + 2_000L, readyAt));
        assertEquals(1, RhythmGameService.countdownNumber(joinedAt + 2_999L, readyAt));
    }
}
