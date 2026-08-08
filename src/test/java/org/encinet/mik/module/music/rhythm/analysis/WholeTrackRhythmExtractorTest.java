package org.encinet.mik.module.music.rhythm.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WholeTrackRhythmExtractorTest {

    @Test
    void waitsForFlushBeforePublishingTheBatchTrackedChart() {
        RhythmTimeline timeline = new RhythmTimeline("whole-track");
        WholeTrackRhythmExtractor extractor = new WholeTrackRhythmExtractor(
                timeline, 1_000, 0L);

        for (int window = 0; window < 350; window++) {
            float amplitude = window >= 25 && (window - 25) % 25 == 0
                    ? ((window / 25) % 2 == 0 ? 0.70F : 0.055F)
                    : 0.004F;
            extractor.accept(stereo(amplitude), 0, 20);
        }

        assertEquals(0, timeline.baseBeatCount(),
                "whole-track analysis must not leak provisional candidates");
        extractor.flush();

        assertTrue(timeline.baseBeatCount() >= 9,
                () -> "tracked beats=" + timeline.baseBeatCount());
        assertTrue(timeline.analyzedThroughMillis() >= 7_000L);
    }

    @Test
    void extractsAQuietFastPulseTrainWithoutConservativeLevelPeaks() {
        RhythmTimeline timeline = new RhythmTimeline("quiet-fast-track");
        WholeTrackRhythmExtractor extractor = new WholeTrackRhythmExtractor(
                timeline, 1_000, 0L);

        for (int window = 0; window < 320; window++) {
            float amplitude = window >= 12 && (window - 12) % 12 == 0
                    ? 0.0012F : 0.00012F;
            extractor.accept(stereo(amplitude), 0, 20);
        }
        extractor.flush();

        assertTrue(timeline.baseBeatCount() >= 20,
                () -> "quiet tracked beats=" + timeline.baseBeatCount());
    }

    @Test
    void detectsTransientsInsideAContinuouslyCompressedLoudSection() {
        RhythmTimeline timeline = new RhythmTimeline("compressed-track");
        WholeTrackRhythmExtractor extractor = new WholeTrackRhythmExtractor(
                timeline, 1_000, 0L);

        for (int window = 0; window < 320; window++) {
            float amplitude = window >= 15 && (window - 15) % 20 == 0
                    ? 0.17F : 0.12F;
            extractor.accept(stereo(amplitude), 0, 20);
        }
        extractor.flush();

        assertTrue(timeline.baseBeatCount() >= 13,
                () -> "compressed tracked beats=" + timeline.baseBeatCount());
    }

    @Test
    void detectsDecodedStyleDecayPulsesAtACommonSampleRate() {
        int sampleRate = 8_000;
        int sampleCount = sampleRate * 4;
        float[][] samples = new float[2][sampleCount];
        for (int sample = 0; sample < sampleCount; sample++) {
            int pulsePosition = sample < 1_000
                    ? Integer.MAX_VALUE : (sample - 1_000) % 2_000;
            double envelope = pulsePosition < 360
                    ? Math.exp(-pulsePosition / 105.0) : 0.0;
            float value = (float) (envelope * Math.sin(2.0 * Math.PI * 95.0
                    * sample / sampleRate) * 0.55);
            samples[0][sample] = value;
            samples[1][sample] = value;
        }
        RhythmTimeline timeline = new RhythmTimeline("decoded-style-pulses");
        WholeTrackRhythmExtractor extractor = new WholeTrackRhythmExtractor(
                timeline, sampleRate, 0L);

        for (int offset = 0; offset < sampleCount; offset += 960) {
            extractor.accept(samples, offset,
                    Math.min(960, sampleCount - offset));
        }
        extractor.flush();

        assertTrue(timeline.baseBeatCount() >= 12,
                () -> "common-rate beats=" + timeline.baseBeatCount());
    }

    @Test
    void detectsDecodedStyleDecayPulsesAfterFortyEightKilohertzResampling() {
        int sampleRate = 48_000;
        int sampleCount = sampleRate * 4;
        float[][] samples = new float[2][sampleCount];
        for (int sample = 0; sample < sampleCount; sample++) {
            int pulsePosition = sample < 6_000
                    ? Integer.MAX_VALUE : (sample - 6_000) % 12_000;
            double envelope = pulsePosition < 2_160
                    ? Math.exp(-pulsePosition / 630.0) : 0.0;
            float value = (float) (envelope * Math.sin(2.0 * Math.PI * 95.0
                    * sample / sampleRate) * 0.55);
            samples[0][sample] = value;
            samples[1][sample] = value;
        }
        RhythmTimeline timeline = new RhythmTimeline("decoded-style-48k-pulses");
        WholeTrackRhythmExtractor extractor = new WholeTrackRhythmExtractor(
                timeline, sampleRate, 0L);

        for (int offset = 0; offset < sampleCount; offset += 960) {
            extractor.accept(samples, offset,
                    Math.min(960, sampleCount - offset));
        }
        extractor.flush();

        assertTrue(timeline.baseBeatCount() >= 12,
                () -> "48k decoded-style beats=" + timeline.baseBeatCount());
    }

    @Test
    void sensitiveWholeTrackProfileStillRejectsLowLevelRandomNoise() {
        RhythmTimeline timeline = new RhythmTimeline("sensitive-noise-guard");
        WholeTrackRhythmExtractor extractor = new WholeTrackRhythmExtractor(
                timeline, 1_000, 0L);
        java.util.Random random = new java.util.Random(17L);

        for (int window = 0; window < 320; window++) {
            float[][] samples = new float[2][20];
            for (int sample = 0; sample < 20; sample++) {
                float value = (random.nextFloat() * 2.0F - 1.0F) * 0.0012F;
                samples[0][sample] = value;
                samples[1][sample] = value;
            }
            extractor.accept(samples, 0, 20);
        }
        extractor.flush();

        assertEquals(0, timeline.baseBeatCount());
    }

    private static float[][] stereo(float value) {
        float[][] samples = new float[2][20];
        java.util.Arrays.fill(samples[0], value);
        java.util.Arrays.fill(samples[1], value);
        return samples;
    }
}
