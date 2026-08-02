package org.encinet.mik.module.menu;

/** Local menu-space coordinates: right, up and toward the player. */
public record FloatingMenuPoint(double right, double up, double forward) {
    public static final FloatingMenuPoint ORIGIN = new FloatingMenuPoint(0, 0, 0);

    public FloatingMenuPoint {
        if (!Double.isFinite(right) || !Double.isFinite(up) || !Double.isFinite(forward)) {
            throw new IllegalArgumentException("Menu coordinates must be finite");
        }
    }
}
