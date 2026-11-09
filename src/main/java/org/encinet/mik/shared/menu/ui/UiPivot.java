package org.encinet.mik.shared.menu.ui;

/** Fractions of the measured rectangle, plus depth in UI units. Pivots may lie outside the rectangle. */
public record UiPivot(double xFraction, double yFraction, double depth) {
    public static final UiPivot CENTER = new UiPivot(.5, .5, 0);
    public UiPivot {
        if (!Double.isFinite(xFraction) || !Double.isFinite(yFraction) || !Double.isFinite(depth))
            throw new IllegalArgumentException("Spatial pivot must be finite");
    }
}
