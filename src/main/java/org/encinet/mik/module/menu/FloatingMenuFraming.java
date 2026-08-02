package org.encinet.mik.module.menu;

/**
 * Defines how much of the player's view a spatial scene may occupy before it
 * is automatically reduced. Panoramic scenes intentionally invite small head
 * turns instead of forcing a dense wall into the central viewing cone.
 */
public record FloatingMenuFraming(double horizontalHalfAngleDegrees,
                                  double verticalHalfAngleDegrees) {
    public static final FloatingMenuFraming COMFORTABLE =
            new FloatingMenuFraming(42.0, 32.0);
    public static final FloatingMenuFraming PANORAMIC =
            new FloatingMenuFraming(58.0, 40.0);

    public FloatingMenuFraming {
        if (!validHalfAngle(horizontalHalfAngleDegrees)
                || !validHalfAngle(verticalHalfAngleDegrees)) {
            throw new IllegalArgumentException(
                    "Floating-menu framing angles must be finite and between 0 and 89 degrees");
        }
    }

    double horizontalTangent() {
        return Math.tan(Math.toRadians(horizontalHalfAngleDegrees));
    }

    double verticalTangent() {
        return Math.tan(Math.toRadians(verticalHalfAngleDegrees));
    }

    private static boolean validHalfAngle(double degrees) {
        return Double.isFinite(degrees) && degrees > 0.0 && degrees < 89.0;
    }
}
