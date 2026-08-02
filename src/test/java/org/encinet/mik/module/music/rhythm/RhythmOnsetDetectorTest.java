package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmOnsetDetectorTest {

    @Test
    void detectsPcmTransientWithoutBlockingPlaybackAnalysis() {
        RhythmTimeline timeline = new RhythmTimeline("pulse");
        RhythmOnsetDetector detector = new RhythmOnsetDetector(timeline, 1_000, 0);

        for (int window = 0; window < 15; window++) {
            detector.accept(stereo(0.006F), 0, 20);
        }
        detector.accept(stereo(0.75F), 0, 20);
        for (int window = 0; window < 15; window++) {
            detector.accept(stereo(0.006F), 0, 20);
        }
        detector.accept(stereo(0.85F), 0, 20);
        detector.accept(stereo(0.006F), 0, 20);

        assertTrue(timeline.baseCueCount() >= 2,
                "detected cues=" + timeline.baseCueCount());
        assertTrue(timeline.analyzedThroughMillis() >= 640);
    }

    @Test
    void sustainedLoudnessDoesNotCreatePeriodicFakeBeats() {
        RhythmTimeline timeline = new RhythmTimeline("sustain");
        RhythmOnsetDetector detector = new RhythmOnsetDetector(timeline, 1_000, 0);

        for (int window = 0; window < 15; window++) {
            detector.accept(stereo(0.004F), 0, 20);
        }
        for (int window = 0; window < 80; window++) {
            detector.accept(stereo(0.45F), 0, 20);
        }
        detector.accept(stereo(0.004F), 0, 20);

        assertEquals(1, timeline.baseCueCount());
    }

    @Test
    void subFloorNoiseDoesNotBecomeAChart() {
        RhythmTimeline timeline = new RhythmTimeline("noise-floor");
        RhythmOnsetDetector detector = new RhythmOnsetDetector(timeline, 1_000, 0);

        for (int window = 0; window < 100; window++) {
            detector.accept(stereo(window % 2 == 0 ? 0.001F : 0.003F), 0, 20);
        }

        assertEquals(0, timeline.baseCueCount());
    }

    @Test
    void phaseInvertedStereoStillProducesAnOnset() {
        RhythmTimeline timeline = new RhythmTimeline("phase");
        RhythmOnsetDetector detector = new RhythmOnsetDetector(timeline, 1_000, 0);

        for (int window = 0; window < 15; window++) {
            detector.accept(stereo(0.004F, -0.004F), 0, 20);
        }
        detector.accept(stereo(0.8F, -0.8F), 0, 20);
        detector.accept(stereo(0.004F, -0.004F), 0, 20);

        assertEquals(1, timeline.baseCueCount());
    }

    @Test
    void strongerTransientReceivesGreaterSalience() {
        RhythmTimeline timeline = new RhythmTimeline("salience");
        RhythmOnsetDetector detector = new RhythmOnsetDetector(timeline, 1_000, 0);

        for (int window = 0; window < 15; window++) {
            detector.accept(stereo(0.004F), 0, 20);
        }
        detector.accept(stereo(0.08F), 0, 20);
        for (int window = 0; window < 15; window++) {
            detector.accept(stereo(0.004F), 0, 20);
        }
        detector.accept(stereo(0.8F), 0, 20);
        detector.accept(stereo(0.004F), 0, 20);

        java.util.List<RhythmCue> cues = timeline.between(0, 1_000);
        assertEquals(2, cues.size());
        assertTrue(cues.get(1).strength() > cues.getFirst().strength());
    }

    private static float[][] stereo(float value) {
        return stereo(value, value);
    }

    private static float[][] stereo(float left, float right) {
        float[][] samples = new float[2][20];
        java.util.Arrays.fill(samples[0], left);
        java.util.Arrays.fill(samples[1], right);
        return samples;
    }
}
