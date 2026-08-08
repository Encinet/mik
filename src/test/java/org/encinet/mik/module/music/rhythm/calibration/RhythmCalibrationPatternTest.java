package org.encinet.mik.module.music.rhythm.calibration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmCalibrationPatternTest {
    private static final List<Long> CONSTANT_BEATS = List.of(
            0L, 800L, 1_600L, 2_400L,
            3_200L, 4_000L, 4_800L, 5_600L);

    @Test
    void fixedPhraseUsesAConstantTwoBarQuarterNotePulse() {
        RhythmCalibrationPattern pattern = RhythmCalibrationPattern.fixed();

        assertEquals(8, pattern.cueCount());
        assertEquals(6_400L, pattern.durationMillis());
        assertEquals(CONSTANT_BEATS, pattern.cueTimesMillis());
        for (int cue = 0; cue < pattern.cueCount(); cue++) {
            long withinBar = pattern.cueTimesMillis().get(cue) % 3_200L;
            double expectedStrength = withinBar == 0L ? 1.0
                    : withinBar == 1_600L ? 0.92 : 0.78;
            assertEquals(expectedStrength, pattern.strength(cue));
        }
    }

    @Test
    void nearestCueRemainsStableAcrossPhraseLoopBoundaries() {
        RhythmCalibrationPattern pattern = RhythmCalibrationPattern.fixed();
        for (long index = 0L; index < pattern.cueCount() * 3L; index++) {
            long cueTime = pattern.cueTimeMillis(index);
            assertEquals(index, pattern.cueIndexNearest(cueTime));
            assertEquals(index, pattern.cueIndexNearest(cueTime + 350L));
            if (cueTime >= 350L) {
                assertEquals(index,
                        pattern.cueIndexNearest(cueTime - 350L));
            }
        }
        assertEquals(pattern.cueCount(),
                pattern.cueIndexAtOrAfter(pattern.durationMillis()));
    }
}
