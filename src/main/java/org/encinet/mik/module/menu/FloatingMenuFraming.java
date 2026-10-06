package org.encinet.mik.module.menu;

/**
 * Declares a scene's interaction cone. The renderer expands it to include
 * actual nodes when a dense layout calls for a small head turn.
 */
public record FloatingMenuFraming(double horizontalHalfAngleDegrees,
                                  double verticalHalfAngleDegrees) {
    public static final FloatingMenuFraming COMFORTABLE =
            new FloatingMenuFraming(42.0, 32.0);
    public static final FloatingMenuFraming PANORAMIC =
            new FloatingMenuFraming(58.0, 40.0);
    public static final FloatingMenuFraming WIDE_ARC =
            new FloatingMenuFraming(82.0, 40.0);

    public FloatingMenuFraming {
        if (!validHalfAngle(horizontalHalfAngleDegrees)
                || !validHalfAngle(verticalHalfAngleDegrees)) {
            throw new IllegalArgumentException(
                    "Floating-menu framing angles must be finite and between 0 and 89 degrees");
        }
    }

    public double horizontalTangent() {
        return Math.tan(Math.toRadians(horizontalHalfAngleDegrees));
    }

    public double verticalTangent() {
        return Math.tan(Math.toRadians(verticalHalfAngleDegrees));
    }

    private static boolean validHalfAngle(double degrees) {
        return Double.isFinite(degrees) && degrees > 0.0 && degrees < 89.0;
    }
}
