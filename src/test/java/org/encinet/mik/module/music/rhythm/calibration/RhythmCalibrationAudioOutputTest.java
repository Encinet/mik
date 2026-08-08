package org.encinet.mik.module.music.rhythm.calibration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmCalibrationAudioOutputTest {

    @Test
    void stallDetectionStartsOnlyAfterTheFullSilenceLimit() {
        RhythmCalibrationAudioOutput.PlaybackProgress progress =
                new RhythmCalibrationAudioOutput.PlaybackProgress(
                        1_000L, 0L, 0);

        assertFalse(progress.stalled(1_600L, 600L));
        assertTrue(progress.stalled(1_601L, 600L));
        assertFalse(new RhythmCalibrationAudioOutput.PlaybackProgress(
                Long.MIN_VALUE, 0L, 0).stalled(Long.MAX_VALUE, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> progress.stalled(2_000L, -1L));
    }

    @Test
    void stallDetectionSurvivesNanoTimeSignedWrap() {
        long lastFrame = Long.MAX_VALUE - 10L;
        long now = Long.MIN_VALUE + 20L;
        RhythmCalibrationAudioOutput.PlaybackProgress progress =
                new RhythmCalibrationAudioOutput.PlaybackProgress(
                        lastFrame, 0L, 0);

        assertFalse(progress.stalled(now, 31L));
        assertTrue(progress.stalled(now, 30L));
    }
}
