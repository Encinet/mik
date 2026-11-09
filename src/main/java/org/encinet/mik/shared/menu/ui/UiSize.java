package org.encinet.mik.shared.menu.ui;

public record UiSize(double width, double height) {
    public static final UiSize ZERO = new UiSize(0, 0);

    public UiSize {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 0 || height < 0) {
            throw new IllegalArgumentException("UI size must be finite and non-negative");
        }
    }

    public static UiSize square(UiLength length) { return new UiSize(length.value(), length.value()); }
}
