package org.encinet.mik.module.menu;

/** Two-dimensional footprint of a scene node in menu-space blocks. */
public record FloatingMenuSize(double width, double height) {
    public FloatingMenuSize {
        if (!positiveFinite(width) || !positiveFinite(height)) {
            throw new IllegalArgumentException("Node size must be positive and finite");
        }
    }

    public FloatingMenuSize max(FloatingMenuSize other) {
        if (other == null) throw new NullPointerException("other");
        return new FloatingMenuSize(Math.max(width, other.width),
                Math.max(height, other.height));
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
