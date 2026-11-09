package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** A bounded table whose header and variable-height pages share one set of column widths. */
public record UiPagedTable(UiContainer table, List<UiKey> rowKeys, int pageIndex,
                           IntConsumer selectPage) implements UiNode {
    public UiPagedTable {
        Objects.requireNonNull(table); Objects.requireNonNull(selectPage);
        rowKeys = List.copyOf(rowKeys);
        if (!(table.layout() instanceof UiTrackGridLayout layout)
                || table.children().size() != (rowKeys.size() + 1) * layout.columns())
            throw new IllegalArgumentException("Paged tables require complete header and data rows");
        if (rowKeys.size() > UiTable.MAX_ROWS || new java.util.HashSet<>(rowKeys).size() != rowKeys.size()
                || rowKeys.contains(UiKey.AUTO)) throw new IllegalArgumentException("Table rows need unique stable keys");
    }

    @Override public UiKey key() { return table.key(); }
    @Override public UiStyle style() { return table.style(); }
    @Override public UiSizing sizing() { return table.sizing(); }
    public List<UiChild> children() { return List.of(UiChild.of(table)); }
}
