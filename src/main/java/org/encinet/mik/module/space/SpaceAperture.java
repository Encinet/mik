package org.encinet.mik.module.space;

/** The rectangular opening of an invisible spatial seam. */
public record SpaceAperture(double width, double height) {

    private static final double CONTAINMENT_EPSILON = 1.0E-7;

    public SpaceAperture {
        requirePositiveFinite(width, "width");
        requirePositiveFinite(height, "height");
    }

    public double halfWidth() {
        return width / 2.0;
    }

    public double halfHeight() {
        return height / 2.0;
    }

    public boolean contains(double localX, double localY) {
        return Math.abs(localX) <= halfWidth() + CONTAINMENT_EPSILON
                && Math.abs(localY) <= halfHeight() + CONTAINMENT_EPSILON;
    }

    boolean approximatelyEquals(SpaceAperture other) {
        return Math.abs(width - other.width) <= CONTAINMENT_EPSILON
                && Math.abs(height - other.height) <= CONTAINMENT_EPSILON;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    "Space aperture " + name + " must be positive and finite");
        }
    }
}
