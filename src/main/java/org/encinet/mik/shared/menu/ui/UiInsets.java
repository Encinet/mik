package org.encinet.mik.shared.menu.ui;

public record UiInsets(double top, double end, double bottom, double start) {
    public static final UiInsets NONE = new UiInsets(0, 0, 0, 0);

    public UiInsets {
        if (!Double.isFinite(top) || !Double.isFinite(end) || !Double.isFinite(bottom)
                || !Double.isFinite(start) || Math.min(Math.min(top, end), Math.min(bottom, start)) < 0) {
            throw new IllegalArgumentException("Invalid UI insets");
        }
    }

    public static UiInsets all(UiLength value) {
        return new UiInsets(value.value(), value.value(), value.value(), value.value());
    }

    public double horizontal() { return start + end; }
    public double vertical() { return top + bottom; }
}
