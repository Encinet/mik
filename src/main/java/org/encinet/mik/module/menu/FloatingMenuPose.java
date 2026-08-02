package org.encinet.mik.module.menu;

import java.util.Objects;

/** Position and local panel orientation within a menu's stable spatial frame. */
public record FloatingMenuPose(
        FloatingMenuPoint point,
        double yawDegrees,
        double pitchDegrees
) {
    public static final FloatingMenuPose ORIGIN = at(FloatingMenuPoint.ORIGIN);

    public FloatingMenuPose {
        point = Objects.requireNonNull(point, "point");
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("Menu orientation must be finite");
        }
    }

    public static FloatingMenuPose at(FloatingMenuPoint point) {
        return new FloatingMenuPose(point, 0.0, 0.0);
    }

    public static FloatingMenuPose oriented(FloatingMenuPoint point,
                                            double yawDegrees, double pitchDegrees) {
        return new FloatingMenuPose(point, yawDegrees, pitchDegrees);
    }

    public double right() {
        return point.right();
    }

    public double up() {
        return point.up();
    }

    public double forward() {
        return point.forward();
    }

    public FloatingMenuPose offset(double right, double up, double forward) {
        return new FloatingMenuPose(new FloatingMenuPoint(
                point.right() + right,
                point.up() + up,
                point.forward() + forward), yawDegrees, pitchDegrees);
    }

    public FloatingMenuPose rotate(double yawDegrees, double pitchDegrees) {
        return new FloatingMenuPose(point,
                this.yawDegrees + yawDegrees,
                this.pitchDegrees + pitchDegrees);
    }
}
