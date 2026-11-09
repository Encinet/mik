package org.encinet.mik.shared.menu;

import java.util.Objects;
import org.encinet.mik.shared.menu.ui.UiRotation;

/** Position and local panel orientation within a menu's stable spatial frame. */
public record FloatingMenuPose(
        FloatingMenuPoint point,
        double yawDegrees,
        double pitchDegrees,
        double rollDegrees,
        double scale
) {
    public static final FloatingMenuPose ORIGIN = at(FloatingMenuPoint.ORIGIN);

    public FloatingMenuPose {
        point = Objects.requireNonNull(point, "point");
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees) || !Double.isFinite(rollDegrees)
                || !Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Menu orientation must be finite");
        }
    }

    public static FloatingMenuPose at(FloatingMenuPoint point) {
        return new FloatingMenuPose(point, 0.0, 0.0, 0.0, 1);
    }

    public static FloatingMenuPose oriented(FloatingMenuPoint point,
                                            double yawDegrees, double pitchDegrees) {
        return new FloatingMenuPose(point, yawDegrees, pitchDegrees, 0, 1);
    }

    public static FloatingMenuPose transformed(FloatingMenuPoint point, UiRotation rotation, double scale) {
        var angles = rotation.angles();
        return new FloatingMenuPose(point, angles.yaw(), angles.pitch(), angles.roll(), scale);
    }
    public UiRotation rotation() { return UiRotation.degrees(yawDegrees, pitchDegrees, rollDegrees); }

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
                point.forward() + forward), yawDegrees, pitchDegrees, rollDegrees, scale);
    }

    public FloatingMenuPose rotate(double yawDegrees, double pitchDegrees) {
        return transformed(point, rotation().compose(UiRotation.degrees(yawDegrees, pitchDegrees, 0)), scale);
    }
}
