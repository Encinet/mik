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

    @Test
    void extractsSoftPitchedAttacksWithoutAPercussionTrack() {
        int sampleRate = 8_000;
        int sampleCount = sampleRate * 7;
        int noteSamples = sampleRate / 2;
        int firstNote = sampleRate / 2;
        double[] frequencies = {220.0, 330.0, 247.0, 392.0};
        float[][] samples = new float[2][sampleCount];
        double phase = 0.0;
        for (int sample = firstNote; sample < sampleCount; sample++) {
            int relative = sample - firstNote;
            int note = relative / noteSamples;
            int insideNote = relative % noteSamples;
            double frequency = frequencies[note % frequencies.length];
            double attack = Math.min(1.0, insideNote / (sampleRate * 0.075));
            double amplitude = 0.025 + attack * 0.105;
            phase += 2.0 * Math.PI * frequency / sampleRate;
            float value = (float) (Math.sin(phase) * amplitude);
            samples[0][sample] = value;
            samples[1][sample] = value;
        }
        RhythmTimeline timeline = extract("soft-pitched", samples, sampleRate);

        assertTrue(timeline.baseBeatCount() >= 10,
                () -> "soft pitched beats=" + beatTimes(timeline, sampleCount,
                        sampleRate));
    }

    @Test
    void sustainedVibratoDoesNotBecomeARepeatedBeatGrid() {
        int sampleRate = 8_000;
        int sampleCount = sampleRate * 6;
        int onset = sampleRate / 2;
        float[][] samples = new float[2][sampleCount];
        double phase = 0.0;
        for (int sample = onset; sample < sampleCount; sample++) {
            double time = (sample - onset) / (double) sampleRate;
            double frequency = 440.0 + 38.0 * Math.sin(2.0 * Math.PI * 5.5 * time);
            double attack = Math.min(1.0, (sample - onset) / (sampleRate * 0.08));
            phase += 2.0 * Math.PI * frequency / sampleRate;
            float value = (float) (Math.sin(phase) * 0.22 * attack);
            samples[0][sample] = value;
            samples[1][sample] = value;
        }
        RhythmTimeline timeline = extract("vibrato-guard", samples, sampleRate);

        assertTrue(timeline.baseBeatCount() <= 2,
                () -> "vibrato false beats=" + beatTimes(timeline, sampleCount,
                        sampleRate));
    }

    @Test
    void extractsRapidDjSubdivisionsFromDecodedPcm() {
        int sampleRate = 8_000;
        int sampleCount = sampleRate * 4;
        int period = sampleRate / 8;
        int firstPulse = sampleRate / 4;
        float[][] samples = new float[2][sampleCount];
        for (int sample = firstPulse; sample < sampleCount; sample++) {
            int pulsePosition = (sample - firstPulse) % period;
            if (pulsePosition >= 120) continue;
            double envelope = Math.exp(-pulsePosition / 32.0);
            double carrier = Math.sin(2.0 * Math.PI * 105.0
                    * sample / sampleRate);
            float value = (float) (envelope * carrier * 0.42);
            samples[0][sample] = value;
            samples[1][sample] = value;
        }
        RhythmTimeline timeline = extract("rapid-dj", samples, sampleRate);

        assertTrue(timeline.baseBeatCount() >= 27,
                () -> "rapid DJ beats=" + beatTimes(timeline, sampleCount,
                        sampleRate));
    }

    @Test
    void extractsLayeredAnimePopArrangementWithVocalAndAlternatingDrums() {
        int sampleRate = 8_000;
        int sampleCount = sampleRate * 6;
        int firstBeat = sampleRate / 2;
        int beatPeriod = sampleRate / 4;
        float[][] samples = new float[2][sampleCount];
        double vocalPhase = 0.0;
        for (int sample = 0; sample < sampleCount; sample++) {
            double seconds = sample / (double) sampleRate;
            double vocalFrequency = 330.0 + 16.0
                    * Math.sin(2.0 * Math.PI * 5.0 * seconds);
            vocalPhase += 2.0 * Math.PI * vocalFrequency / sampleRate;
            double vocal = Math.sin(vocalPhase) * 0.085;
            double percussion = 0.0;
            if (sample >= firstBeat) {
                int relative = sample - firstBeat;
                int insideBeat = relative % beatPeriod;
                int beat = relative / beatPeriod;
                if (insideBeat < 280) {
                    double envelope = Math.exp(-insideBeat / 70.0);
                    double frequency = beat % 2 == 0 ? 92.0 : 2_250.0;
                    percussion = Math.sin(2.0 * Math.PI * frequency
                            * sample / sampleRate) * envelope
                            * (beat % 2 == 0 ? 0.38 : 0.24);
                }
            }
            samples[0][sample] = (float) (vocal + percussion * 0.82);
            samples[1][sample] = (float) (vocal + percussion);
        }
        RhythmTimeline timeline = extract("layered-pop", samples, sampleRate);

        assertTrue(timeline.baseBeatCount() >= 19,
                () -> "layered pop beats=" + beatTimes(timeline, sampleCount,
                        sampleRate));
    }

    private static RhythmTimeline extract(String seed, float[][] samples,
                                          int sampleRate) {
        RhythmTimeline timeline = new RhythmTimeline(seed);
        WholeTrackRhythmExtractor extractor = new WholeTrackRhythmExtractor(
                timeline, sampleRate, 0L);
        for (int offset = 0; offset < samples[0].length; offset += 960) {
            extractor.accept(samples, offset,
                    Math.min(960, samples[0].length - offset));
        }
        extractor.flush();
        return timeline;
    }

    private static java.util.List<Long> beatTimes(RhythmTimeline timeline,
                                                  int sampleCount,
                                                  int sampleRate) {
        return timeline.between(0L, sampleCount * 1_000L / sampleRate).stream()
                .map(RhythmBeat::timeMillis)
                .toList();
    }

    private static float[][] stereo(float value) {
        float[][] samples = new float[2][20];
        java.util.Arrays.fill(samples[0], value);
        java.util.Arrays.fill(samples[1], value);
        return samples;
    }
}
