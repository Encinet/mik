package org.encinet.mik.module.menu;

/**
 * Defines how far a player may drift from a scene's opening point before the
 * menu is closed as a safety measure.
 */
public record FloatingMenuMovementPolicy(double maximumDrift) {
    /** Short leash for ordinary menus that should not remain behind in the world. */
    public static final FloatingMenuMovementPolicy STANDARD =
            new FloatingMenuMovementPolicy(3.0);

    /** Wider safety leash for scenes that intentionally capture movement keys. */
    public static final FloatingMenuMovementPolicy CAPTURED_INPUT =
            new FloatingMenuMovementPolicy(12.0);

    public FloatingMenuMovementPolicy {
        if (!Double.isFinite(maximumDrift) || maximumDrift <= 0.0) {
            throw new IllegalArgumentException("Maximum menu drift must be positive and finite");
        }
    }

    boolean exceeded(double distanceSquared) {
        return !Double.isFinite(distanceSquared)
                || distanceSquared > maximumDrift * maximumDrift;
    }
}
