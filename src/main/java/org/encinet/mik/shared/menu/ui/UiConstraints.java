package org.encinet.mik.shared.menu.ui;

/** Finite constraints, including for scrollable and custom layout content. */
public record UiConstraints(double minWidth, double maxWidth, double minHeight, double maxHeight) {
    public UiConstraints {
        if (!Double.isFinite(minWidth) || !Double.isFinite(maxWidth)
                || !Double.isFinite(minHeight) || !Double.isFinite(maxHeight)
                || minWidth < 0 || minHeight < 0 || maxWidth < minWidth || maxHeight < minHeight) {
            throw new IllegalArgumentException("Invalid finite UI constraints");
        }
    }

    public static UiConstraints bounded(double width, double height) {
        return new UiConstraints(0, width, 0, height);
    }

    public UiSize constrain(UiSize size) {
        return new UiSize(Math.clamp(size.width(), minWidth, maxWidth),
                Math.clamp(size.height(), minHeight, maxHeight));
    }

    public UiConstraints inset(UiInsets insets) {
        double width = Math.max(0, maxWidth - insets.horizontal());
        double height = Math.max(0, maxHeight - insets.vertical());
        return new UiConstraints(Math.min(width, Math.max(0, minWidth - insets.horizontal())), width,
                Math.min(height, Math.max(0, minHeight - insets.vertical())), height);
    }
}
