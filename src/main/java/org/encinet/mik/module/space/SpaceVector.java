package org.encinet.mik.module.space;

/** Immutable vector used by the spatial routing model without depending on Bukkit state. */
public record SpaceVector(double x, double y, double z) {

    public static final SpaceVector ZERO = new SpaceVector(0.0, 0.0, 0.0);
    private static final double MINIMUM_LENGTH_SQUARED = 1.0E-12;

    public SpaceVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Space vector components must be finite");
        }
    }

    public SpaceVector add(SpaceVector other) {
        return new SpaceVector(x + other.x, y + other.y, z + other.z);
    }

    public SpaceVector subtract(SpaceVector other) {
        return new SpaceVector(x - other.x, y - other.y, z - other.z);
    }

    public SpaceVector multiply(double factor) {
        return new SpaceVector(x * factor, y * factor, z * factor);
    }

    public double dot(SpaceVector other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public SpaceVector cross(SpaceVector other) {
        return new SpaceVector(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public SpaceVector normalized() {
        double squared = lengthSquared();
        if (squared < MINIMUM_LENGTH_SQUARED) {
            throw new IllegalArgumentException("Cannot normalize a zero-length space vector");
        }
        return multiply(1.0 / Math.sqrt(squared));
    }
}
