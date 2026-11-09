package org.encinet.mik.shared.menu.ui;

import java.util.Objects;

/** Component-owned hard bounds; natural size and parent allocation belong to the layout. */
public record UiSizing(UiSize minimum, UiSize maximum) {
    public static final UiSizing CONTENT = new UiSizing(UiSize.ZERO, new UiSize(4096, 4096));

    public UiSizing {
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        if (minimum.width() > maximum.width() || minimum.height() > maximum.height()) {
            throw new IllegalArgumentException("UI minimum must not exceed maximum");
        }
    }

    public static UiSizing fixed(UiSize size) { return new UiSizing(size, size); }
    public static UiSizing atLeast(UiSize size) { return new UiSizing(size, CONTENT.maximum()); }
    public static UiSizing width(UiLength minimum, UiLength maximum) {
        return new UiSizing(new UiSize(minimum.value(), 0), new UiSize(maximum.value(), 4096));
    }
    public UiSizing atLeastHeight(UiLength height) {
        return new UiSizing(new UiSize(minimum.width(), Math.max(minimum.height(), height.value())), maximum);
    }
    public UiConstraints within(UiConstraints parent) {
        double minWidth = Math.max(minimum.width(), parent.minWidth());
        double minHeight = Math.max(minimum.height(), parent.minHeight());
        double maxWidth = Math.min(maximum.width(), parent.maxWidth());
        double maxHeight = Math.min(maximum.height(), parent.maxHeight());
        if (minWidth > maxWidth || minHeight > maxHeight) {
            throw new UiOverflowException("Component size does not fit its allocated constraints");
        }
        return new UiConstraints(minWidth, maxWidth, minHeight, maxHeight);
    }
}
