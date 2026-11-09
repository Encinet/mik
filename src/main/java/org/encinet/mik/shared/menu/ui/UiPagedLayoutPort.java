package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Declaration adapter for a paged collection using an ordinary layout. */
public record UiPagedLayoutPort(UiPort content, int pageIndex, IntConsumer selectPage,
                              boolean compactModels) implements UiNode {
    public UiPagedLayoutPort {
        Objects.requireNonNull(content); Objects.requireNonNull(selectPage);
    }
    @Override public UiKey key() { return content.key(); }
    @Override public UiStyle style() { return content.style(); }
    @Override public UiSizing sizing() { return content.sizing(); }
    public UiPagedLayoutPort withSizing(UiSizing sizing) {
        return new UiPagedLayoutPort(new UiPort(content.key(), content.layout(), content.style(), sizing,
                content.sources(), content.textRole(), content.itemSlot(), content.summaryLines()),
                pageIndex, selectPage, compactModels);
    }
    public UiPagedLayoutPort withCompactModels() { return new UiPagedLayoutPort(content, pageIndex, selectPage, true); }
}
