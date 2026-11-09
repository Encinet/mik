package org.encinet.mik.shared.menu.ui;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Construction-time declaration adapter for a measured, feature-owned page of existing content entries. */
public record UiPagedPort(UiPort content, int rows, int pageIndex, IntConsumer selectPage, boolean compactModels) implements UiNode {
    public UiPagedPort(UiPort content, int rows, int pageIndex, IntConsumer selectPage) {
        this(content, rows, pageIndex, selectPage, false);
    }
    public UiPagedPort {
        Objects.requireNonNull(content); Objects.requireNonNull(selectPage);
        if (rows < 1) throw new IllegalArgumentException("A flow port needs rows");
    }
    @Override public UiKey key() { return content.key(); }
    @Override public UiStyle style() { return content.style(); }
    @Override public UiSizing sizing() { return content.sizing(); }
    public UiPagedPort withSizing(UiSizing sizing) {
        return new UiPagedPort(new UiPort(content.key(), content.layout(), content.style(), sizing,
                content.sources(), content.textRole(), content.itemSlot(), content.summaryLines(), content.itemTextWidth()),
                rows, pageIndex, selectPage, compactModels);
    }
    public UiPagedPort withCompactModels() { return new UiPagedPort(content, rows, pageIndex, selectPage, true); }
}
