package org.encinet.mik.shared.menu.ui;

/** A logical interface length. Conversion to world space belongs to the presenter. */
public record UiLength(double value) {
    public static final UiLength ZERO = new UiLength(0);

    public UiLength {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("UI length must be finite and non-negative");
        }
    }

    public static UiLength ui(double value) { return new UiLength(value); }
}
