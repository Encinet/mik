package org.encinet.mik.module.space;

import java.util.Objects;

/**
 * Right-handed local coordinate frame. The forward axis is the direction in which a
 * entity crosses a gateway and emerges from its destination.
 */
public record SpaceFrame(
        SpaceVector origin,
        SpaceVector right,
        SpaceVector up,
        SpaceVector forward
) {

    private static final double ORTHONORMAL_TOLERANCE = 1.0E-6;

    public SpaceFrame {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(up, "up");
        Objects.requireNonNull(forward, "forward");
        requireUnit(right, "right");
        requireUnit(up, "up");
        requireUnit(forward, "forward");
        requirePerpendicular(right, up, "right/up");
        requirePerpendicular(right, forward, "right/forward");
        requirePerpendicular(up, forward, "up/forward");
        if (right.cross(forward).dot(up) < 1.0 - ORTHONORMAL_TOLERANCE) {
            throw new IllegalArgumentException("Space frame axes must use right × forward = up");
        }
    }

    public static SpaceFrame oriented(
            SpaceVector origin,
            double yawDegrees,
            double pitchDegrees,
            double rollDegrees
    ) {
        if (!Double.isFinite(yawDegrees)
                || !Double.isFinite(pitchDegrees)
                || !Double.isFinite(rollDegrees)) {
            throw new IllegalArgumentException("Space frame angles must be finite");
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(Math.clamp(pitchDegrees, -90.0, 90.0));
        double roll = Math.toRadians(rollDegrees);
        double cosPitch = Math.cos(pitch);
        SpaceVector forward = new SpaceVector(
                -cosPitch * Math.sin(yaw),
                -Math.sin(pitch),
                cosPitch * Math.cos(yaw)).normalized();
        SpaceVector baseRight = new SpaceVector(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        SpaceVector baseUp = baseRight.cross(forward).normalized();
        SpaceVector right = baseRight.multiply(Math.cos(roll))
                .add(baseUp.multiply(Math.sin(roll))).normalized();
        SpaceVector up = baseUp.multiply(Math.cos(roll))
                .subtract(baseRight.multiply(Math.sin(roll))).normalized();
        return new SpaceFrame(origin, right, up, forward);
    }

    public static SpaceFrame fromAxes(
            SpaceVector origin,
            SpaceVector forward,
            SpaceVector upHint
    ) {
        SpaceVector normalizedForward = forward.normalized();
        SpaceVector projectedUp = upHint.subtract(
                normalizedForward.multiply(upHint.dot(normalizedForward)));
        SpaceVector normalizedUp = projectedUp.normalized();
        SpaceVector right = normalizedForward.cross(normalizedUp).normalized();
        SpaceVector up = right.cross(normalizedForward).normalized();
        return new SpaceFrame(origin, right, up, normalizedForward);
    }

    public SpaceVector toLocalPoint(SpaceVector point) {
        return toLocalVector(point.subtract(origin));
    }

    public SpaceVector fromLocalPoint(SpaceVector point) {
        return origin.add(fromLocalVector(point));
    }

    public SpaceVector toLocalVector(SpaceVector vector) {
        return new SpaceVector(vector.dot(right), vector.dot(up), vector.dot(forward));
    }

    public SpaceVector fromLocalVector(SpaceVector vector) {
        return right.multiply(vector.x())
                .add(up.multiply(vector.y()))
                .add(forward.multiply(vector.z()));
    }

    /** Returns the same physical plane viewed from its opposite side. */
    public SpaceFrame reversed() {
        return new SpaceFrame(origin, right.multiply(-1.0), up, forward.multiply(-1.0));
    }

    private static void requireUnit(SpaceVector vector, String name) {
        if (Math.abs(vector.lengthSquared() - 1.0) > ORTHONORMAL_TOLERANCE) {
            throw new IllegalArgumentException("Space frame " + name + " axis must be normalized");
        }
    }

    private static void requirePerpendicular(SpaceVector first, SpaceVector second, String name) {
        if (Math.abs(first.dot(second)) > ORTHONORMAL_TOLERANCE) {
            throw new IllegalArgumentException("Space frame " + name + " axes must be perpendicular");
        }
    }
}
