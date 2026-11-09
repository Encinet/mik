package org.encinet.mik.shared.menu.ui;

import java.util.*;
import java.util.function.*;
import org.encinet.mik.shared.menu.FloatingMenuPage;

/** Variable-height reading items, with a persistent header and measured pagination inside one surface. */
public record UiPagedList(UiKey key, UiNode header, List<UiChild> items, UiLength gap, int pageIndex,
                          Function<FloatingMenuPage,UiNode> pager, IntConsumer selectPage, UiStyle style, UiSizing sizing) implements UiNode {
    public UiPagedList {
        Objects.requireNonNull(key); Objects.requireNonNull(header); Objects.requireNonNull(gap);
        Objects.requireNonNull(pager); Objects.requireNonNull(selectPage); Objects.requireNonNull(style); Objects.requireNonNull(sizing);
        items = List.copyOf(items);
        new UiContainer(UiKey.named("items"),UiLayouts.column(gap),items,UiStyle.PLAIN,UiSizing.CONTENT);
    }
    public List<UiChild> children() {
        return List.of(UiChild.linear(UiKey.named("header"),header,LinearSlot.CONTENT),UiChild.of(
                new UiContainer(UiKey.named("items"),UiLayouts.column(gap),items,UiStyle.PLAIN,UiSizing.CONTENT)));
    }
}
