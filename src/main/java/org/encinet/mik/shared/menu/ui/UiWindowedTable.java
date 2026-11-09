package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Equal-height table rows, with template-sized or visible-content-sized columns. */
public record UiWindowedTable(UiContainer template, int itemCount, IntFunction<UiContainer> row,
                              int pageIndex, IntConsumer selectPage, java.util.OptionalInt anchorIndex, UiPageControls pager,
                              boolean visibleColumns) implements UiNode {
    public UiWindowedTable {
        Objects.requireNonNull(template); Objects.requireNonNull(row); Objects.requireNonNull(selectPage);
        Objects.requireNonNull(anchorIndex); Objects.requireNonNull(pager);
        if (anchorIndex.isPresent() && anchorIndex.getAsInt() < 0) throw new IllegalArgumentException("Negative table anchor");
        if (!(template.layout() instanceof UiTrackGridLayout layout)
                || template.children().size() != 2 * layout.columns() || itemCount < 0)
            throw new IllegalArgumentException("Windowed tables need a header, one row template and a nonnegative count");
    }

    public UiWindowedTable(UiContainer template, int count, IntFunction<UiContainer> row, int page, IntConsumer select) {
        this(template,count,row,page,select,java.util.OptionalInt.empty(),UiTemplates::pager,false);
    }

    public UiWindowedTable page(int index, IntConsumer select) { return new UiWindowedTable(template,itemCount,row,index,select,java.util.OptionalInt.empty(),pager,visibleColumns); }
    public UiWindowedTable containing(int index) { return new UiWindowedTable(template,itemCount,row,pageIndex,selectPage,java.util.OptionalInt.of(index),pager,visibleColumns); }
    public UiWindowedTable pager(UiPageControls controls) { return new UiWindowedTable(template,itemCount,row,pageIndex,selectPage,anchorIndex,controls,visibleColumns); }
    /** Fit shared columns to the current window; the template still establishes the uniform row height. */
    public UiWindowedTable fitVisibleColumns() { return new UiWindowedTable(template,itemCount,row,pageIndex,selectPage,anchorIndex,pager,true); }
    @Override public UiKey key() { return template.key(); }
    @Override public UiStyle style() { return template.style(); }
    @Override public UiSizing sizing() { return template.sizing(); }
    /** Declaration traversal exposes only the header; rows are created when the window is measured. */
    public List<UiChild> children() {
        return template.children().subList(0,((UiTrackGridLayout) template.layout()).columns());
    }
}
