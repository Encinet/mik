package org.encinet.mik.shared.menu;

/** Immutable animation policy shared by a complete menu hierarchy. */
public record FloatingMenuAnimation(
        int openingTicks,
        int closingTicks,
        int pressTicks,
        double entryOffset,
        double hoverOffset,
        double pressOffset,
        double idleAmplitude,
        double idleSpeed,
        FloatingMenuEasing openingEasing,
        FloatingMenuEasing closingEasing
) {
    public static final FloatingMenuAnimation DEFAULT =
            new FloatingMenuAnimation(6, 4, 3, 0.12, 0.045, 0.06, 0, 0,
                    FloatingMenuEasing.CUBIC_OUT, FloatingMenuEasing.CUBIC_OUT);

    /** Keeps fades and layout transitions while leaving local content on its declared plane. */
    public static final FloatingMenuAnimation PLANAR =
            new FloatingMenuAnimation(6, 4, 3, 0, 0, 0, 0, 0,
                    FloatingMenuEasing.CUBIC_OUT, FloatingMenuEasing.CUBIC_OUT);

    public FloatingMenuAnimation {
        if (openingTicks < 1 || closingTicks < 1 || pressTicks < 1) {
            throw new IllegalArgumentException("Animation durations must be positive");
        }
        if (!Double.isFinite(entryOffset) || !Double.isFinite(hoverOffset) || !Double.isFinite(pressOffset)
                || !Double.isFinite(idleAmplitude) || !Double.isFinite(idleSpeed)
                || entryOffset < 0 || hoverOffset < 0 || pressOffset < 0 || idleAmplitude < 0 || idleSpeed < 0) {
            throw new IllegalArgumentException("Animation distances and speed cannot be negative");
        }
        java.util.Objects.requireNonNull(openingEasing, "openingEasing");
        java.util.Objects.requireNonNull(closingEasing, "closingEasing");
    }

    public FloatingMenuAnimation reduced() {
        return new FloatingMenuAnimation(1, 1, 1, 0, 0, 0, 0, 0,
                FloatingMenuEasing.CUBIC_OUT, FloatingMenuEasing.CUBIC_OUT);
    }
}
