package org.encinet.mik.module.music.rhythm.analysis;

/**
 * One mode-independent musical accent emitted by a rhythm extractor.
 *
 * <p>No game action or lane is stored here. Modes can project the same pulse
 * into directions, turns, orbit tiles, or any other interaction.</p>
 */
public record RhythmPulse(
        long timeMillis,
        double strength,
        double stereoBalance,
        double toneBalance,
        long signature
) {
    public RhythmPulse(long timeMillis, double strength) {
        this(timeMillis, strength, 0.0, 0.0,
                timeMillis * 0x9E3779B97F4A7C15L);
    }

    public RhythmPulse {
        if (timeMillis < 0L) {
            throw new IllegalArgumentException("pulse time must not be negative");
        }
        requireUnitInterval(strength, "pulse strength");
        requireBalance(stereoBalance, "stereo balance");
        requireBalance(toneBalance, "tone balance");
    }

    private static void requireUnitInterval(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between zero and one");
        }
    }

    private static void requireBalance(double value, String name) {
        if (!Double.isFinite(value) || value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between minus one and one");
        }
    }
}
