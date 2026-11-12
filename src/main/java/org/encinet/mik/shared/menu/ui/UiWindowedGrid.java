package org.encinet.mik.shared.menu.ui;

import java.util.Objects;

/** Responsive equal-height cards sharing the list's bounded, deferred source and page state. */
public record UiWindowedGrid(UiWindowedList items, int maximumColumns, UiLength minimumColumnWidth,
                             UiLength columnGap, boolean centered) implements UiNode {
    public UiWindowedGrid(UiWindowedList items, int maximumColumns, UiLength minimumColumnWidth, UiLength columnGap) {
        this(items, maximumColumns, minimumColumnWidth, columnGap, false);
    }

    public UiWindowedGrid {
        Objects.requireNonNull(items); Objects.requireNonNull(minimumColumnWidth); Objects.requireNonNull(columnGap);
        if (maximumColumns < 1 || maximumColumns > UiWindowedList.MAX_VISIBLE_ITEMS)
            throw new IllegalArgumentException("Window grid column budget exceeded");
        if (minimumColumnWidth.value() <= 0) throw new IllegalArgumentException("Window columns need a positive minimum width");
    }

    /** Center cards within their columns, short rows horizontally and short pages vertically. */
    public UiWindowedGrid centered(boolean value) {
        return new UiWindowedGrid(items, maximumColumns, minimumColumnWidth, columnGap, value);
    }

    public UiKey key() { return items.key(); }
    public UiStyle style() { return items.style(); }
    public UiSizing sizing() { return items.sizing(); }
}
