package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RhythmGameResultTest {
    @Test
    void calculatesWeightedAccuracyAndTimingBias() {
        RhythmGameResult result = new RhythmGameResult(
                12_000L, 17L, 4L, 3L, 2L, 1L, -45L);

        assertEquals(10L, result.totalJudgements());
        assertEquals(0.715, result.accuracy(), 0.000_001);
        assertEquals(-5.0, result.meanTimingErrorMillis(), 0.000_001);
        assertEquals("71.50%", RhythmGameService.accuracyText(result));
    }

    @Test
    void rejectsNegativeCounters() {
        assertThrows(IllegalArgumentException.class, () ->
                new RhythmGameResult(0L, 0L, 0L, 0L, 0L, -1L, 0L));
        assertEquals("—", RhythmGameService.accuracyText(
                new RhythmGameResult(0L, 0L, 0L, 0L, 0L, 0L, 0L)));
    }
}
