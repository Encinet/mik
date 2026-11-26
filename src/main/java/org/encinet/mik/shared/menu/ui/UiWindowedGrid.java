package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.Optional;

/** Responsive equal-height cards sharing the list's bounded, deferred source and page state. */
public record UiWindowedGrid(UiWindowedList items, int maximumColumns, UiLength minimumColumnWidth,
                             UiLength columnGap, boolean centered, Optional<UiColor> foldedBackground,
                             int minimumRows) implements UiNode {
    public UiWindowedGrid(UiWindowedList items, int maximumColumns, UiLength minimumColumnWidth, UiLength columnGap) {
        this(items, maximumColumns, minimumColumnWidth, columnGap, false);
    }

    public UiWindowedGrid(UiWindowedList items, int maximumColumns, UiLength minimumColumnWidth, UiLength columnGap,
                          boolean centered) {
        this(items, maximumColumns, minimumColumnWidth, columnGap, centered, Optional.empty(), 1);
    }

    public UiWindowedGrid {
        Objects.requireNonNull(items); Objects.requireNonNull(minimumColumnWidth); Objects.requireNonNull(columnGap);
        Objects.requireNonNull(foldedBackground);
        if (maximumColumns < 1 || maximumColumns > UiWindowedList.MAX_VISIBLE_ITEMS)
            throw new IllegalArgumentException("Window grid column budget exceeded");
        if (minimumRows < 1 || minimumRows > UiWindowedList.MAX_VISIBLE_ITEMS / maximumColumns)
            throw new IllegalArgumentException("Window grid row budget exceeded");
        if (minimumColumnWidth.value() <= 0) throw new IllegalArgumentException("Window columns need a positive minimum width");
    }

    /** Center cards within their columns, short rows horizontally and short pages vertically. */
    public UiWindowedGrid centered(boolean value) {
        return new UiWindowedGrid(items, maximumColumns, minimumColumnWidth, columnGap, value, foldedBackground, minimumRows);
    }

    /** Arrange the body as three rigid panels when at least three columns fit. All panels share one page. */
    public UiWindowedGrid folded(UiColor background) {
        return new UiWindowedGrid(items, maximumColumns, minimumColumnWidth, columnGap, centered, Optional.of(background), minimumRows);
    }

    /** Lets a responsive alternative use a denser layout if the viewport fits too few rows. */
    public UiWindowedGrid atLeastRows(int rows) {
        return new UiWindowedGrid(items, maximumColumns, minimumColumnWidth, columnGap, centered, foldedBackground, rows);
    }

    public UiKey key() { return items.key(); }
    public UiStyle style() { return items.style(); }
    public UiSizing sizing() { return items.sizing(); }
}
