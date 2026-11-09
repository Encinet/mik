package org.encinet.mik.shared.menu.ui;

import org.encinet.mik.shared.menu.FloatingMenuPage;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** A width-packed collection with a maximum row count; occupied rows determine the shared page size. */
public record UiPagedFlow(UiKey key, List<UiChild> items, int rows, UiLength gap, UiLength rowGap,
                          int pageIndex, Function<FloatingMenuPage, UiNode> pager, IntConsumer selectPage,
                          UiStyle style, UiSizing sizing) implements UiNode {
    public UiPagedFlow {
        Objects.requireNonNull(key);
        items = List.copyOf(items);
        if (rows < 1) throw new IllegalArgumentException("A flow page must have rows");
        Objects.requireNonNull(gap);
        Objects.requireNonNull(rowGap);
        Objects.requireNonNull(pager);
        Objects.requireNonNull(selectPage);
        Objects.requireNonNull(style);
        Objects.requireNonNull(sizing);
        // Validate stable sibling identities just as ordinary containers do.
        new UiContainer(key, UiLayouts.wrap(gap, rowGap), items, style, sizing);
    }
}
