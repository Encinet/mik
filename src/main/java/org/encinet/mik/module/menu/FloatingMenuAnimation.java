package org.encinet.mik.module.menu;

/** Immutable animation policy shared by a complete menu hierarchy. */
public record FloatingMenuAnimation(
        int openingTicks,
        int closingTicks,
        int pressTicks,
        double hoverOffset,
        double pressOffset,
        double idleAmplitude,
        double idleSpeed,
        FloatingMenuEasing openingEasing,
        FloatingMenuEasing closingEasing
) {
    public static final FloatingMenuAnimation DEFAULT =
            new FloatingMenuAnimation(7, 5, 4, 0.075, 0.10, 0.018, 0.10,
                    FloatingMenuEasing.BACK_OUT, FloatingMenuEasing.CUBIC_OUT);

    public FloatingMenuAnimation {
        if (openingTicks < 1 || closingTicks < 1 || pressTicks < 1) {
            throw new IllegalArgumentException("Animation durations must be positive");
        }
        if (!Double.isFinite(hoverOffset) || !Double.isFinite(pressOffset)
                || !Double.isFinite(idleAmplitude) || !Double.isFinite(idleSpeed)
                || hoverOffset < 0 || pressOffset < 0 || idleAmplitude < 0 || idleSpeed < 0) {
            throw new IllegalArgumentException("Animation distances and speed cannot be negative");
        }
        java.util.Objects.requireNonNull(openingEasing, "openingEasing");
        java.util.Objects.requireNonNull(closingEasing, "closingEasing");
    }
}
