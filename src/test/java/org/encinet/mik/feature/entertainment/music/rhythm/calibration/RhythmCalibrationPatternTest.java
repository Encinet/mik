package org.encinet.mik.feature.entertainment.music.rhythm.calibration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmCalibrationPatternTest {
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
