package org.encinet.mik.module.music.rhythm.calibration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlasmoVoiceCalibrationAudioTest {

    @Test
    void synthesizesOneContinuousTimelineWithSilentLeadInAndDistinctDrums() {
        short[] samples = RhythmCalibrationDrumSynth.timeline(
                List.of(1_000L, 2_000L), 3_000L);

        assertEquals(144_000, samples.length);
        assertTrue(allZero(samples, 0, 47_900));
        assertTrue(anyNonZero(samples, 48_000, 52_800));
        assertTrue(allZero(samples, 60_000, 95_900));
        assertTrue(anyNonZero(samples, 96_000, 100_800));
    }

    @Test
    void repeatingPatternIsExactlyAlignedToTwentyMillisecondVoiceFrames() {
        RhythmCalibrationPattern pattern = RhythmCalibrationPattern.fixed();
        short[] samples = RhythmCalibrationDrumSynth.timeline(pattern);

        assertEquals(pattern.durationMillis() * 48L, samples.length);
        assertEquals(0, samples.length % 960,
                "48 kHz audio must contain an exact number of 20 ms frames");
        assertTrue(anyNonZero(samples, 0, 5_280));
        int lastCueSample = Math.toIntExact(
                pattern.cueTimesMillis().getLast() * 48L);
        assertTrue(allZero(samples, lastCueSample + 6_000, samples.length),
                "the loop boundary must stay silent and click-free");
    }

    private static boolean allZero(short[] samples, int from, int to) {
        for (int index = from; index < to; index++) {
            if (samples[index] != 0) return false;
        }
        return true;
    }

    private static boolean anyNonZero(short[] samples, int from, int to) {
        for (int index = from; index < to; index++) {
            if (samples[index] != 0) return true;
        }
        return false;
    }
}
