package org.encinet.mik.module.music.rhythm.calibration;

import java.util.List;

/** Pure PCM synthesis used by the Plasmo Voice calibration adapter. */
final class RhythmCalibrationDrumSynth {
    private static final int SAMPLE_RATE = 48_000;
    private static final int DRUM_MILLIS = 110;

    private RhythmCalibrationDrumSynth() {
    }

    static short[] timeline(RhythmCalibrationPattern pattern) {
        if (pattern == null) throw new NullPointerException("pattern");
        long boundedDuration = boundedDuration(pattern.durationMillis());
        short[] samples = samples(boundedDuration);
        int drumSamples = DRUM_MILLIS * SAMPLE_RATE / 1_000;
        for (int index = 0; index < pattern.cueCount(); index++) {
            long cueMillis = pattern.cueTimesMillis().get(index);
            int start = Math.toIntExact((cueMillis * SAMPLE_RATE) / 1_000L);
            mixDrum(samples, start, drumSamples, pattern.strength(index));
        }
        return samples;
    }

    static short[] timeline(List<Long> cueTimesMillis, long durationMillis) {
        long boundedDuration = boundedDuration(durationMillis);
        short[] samples = samples(boundedDuration);
        int drumSamples = DRUM_MILLIS * SAMPLE_RATE / 1_000;
        for (long cueMillis : cueTimesMillis) {
            if (cueMillis < 0L || cueMillis >= boundedDuration) continue;
            int start = Math.toIntExact((cueMillis * SAMPLE_RATE) / 1_000L);
            mixDrum(samples, start, drumSamples, 1.0);
        }
        return samples;
    }

    private static long boundedDuration(long durationMillis) {
        return Math.clamp(durationMillis, 1L, 120_000L);
    }

    private static short[] samples(long durationMillis) {
        int sampleCount = Math.toIntExact(
                (durationMillis * SAMPLE_RATE) / 1_000L);
        return new short[sampleCount];
    }

    private static void mixDrum(short[] samples, int start, int drumSamples,
                                double strength) {
        for (int offset = 0; offset < drumSamples
                && start + offset < samples.length; offset++) {
            double seconds = offset / (double) SAMPLE_RATE;
            double envelope = Math.exp(-34.0 * seconds);
            double frequency = 105.0 - 280.0 * seconds;
            double body = Math.sin(2.0 * Math.PI * frequency * seconds);
            double click = offset < SAMPLE_RATE / 160
                    ? (((offset * 1_103_515_245L + 12_345L) >>> 15) & 1L)
                    * 2.0 - 1.0
                    : 0.0;
            double value = Math.clamp(strength, 0.0, 1.0)
                    * envelope * (0.82 * body + 0.18 * click);
            int mixed = samples[start + offset]
                    + (int) Math.round(value * 22_000.0);
            samples[start + offset] = (short) Math.clamp(mixed,
                    Short.MIN_VALUE, Short.MAX_VALUE);
        }
    }
}
