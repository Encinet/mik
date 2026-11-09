package org.encinet.mik.shared.menu.ui;

import java.util.Objects;

/** Responsive equal-height cards sharing the list's bounded, deferred source and page state. */
public record UiWindowedGrid(UiWindowedList items, int maximumColumns, UiLength minimumColumnWidth,
                             UiLength columnGap) implements UiNode {
    public UiWindowedGrid {
        Objects.requireNonNull(items); Objects.requireNonNull(minimumColumnWidth); Objects.requireNonNull(columnGap);
        if (maximumColumns < 1 || maximumColumns > UiWindowedList.MAX_VISIBLE_ITEMS)
            throw new IllegalArgumentException("Window grid column budget exceeded");
        if (minimumColumnWidth.value() <= 0) throw new IllegalArgumentException("Window columns need a positive minimum width");
    }

    public UiKey key() { return items.key(); }
    public UiStyle style() { return items.style(); }
    public UiSizing sizing() { return items.sizing(); }
}
