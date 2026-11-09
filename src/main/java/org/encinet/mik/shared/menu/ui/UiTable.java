package org.encinet.mik.shared.menu.ui;

import java.util.*;
import java.util.function.Function;

/** A bounded typed data table. Cells are ordinary UI subtrees and own their usual actions and resources. */
public final class UiTable<T> {
    public static final int MAX_ROWS = 256;
    public record Column<T>(UiKey key, UiNode header, UiTrack track, Function<T, UiNode> content) {
        public Column { Objects.requireNonNull(key); Objects.requireNonNull(header); Objects.requireNonNull(track); Objects.requireNonNull(content); }
    }
    private final List<Column<T>> columns = new ArrayList<>();
    private List<T> rows = List.of();
    private Function<T, UiKey> rowKey;
    private UiKey key = UiKey.AUTO;
    private UiSizing sizing = UiSizing.CONTENT;
    private UiLength horizontalGap = UiLength.ui(12), verticalGap = UiLength.ui(8);
    private UiLayouts.GridAlignment verticalAlignment = UiLayouts.GridAlignment.CENTER;

    public UiTable<T> key(UiKey key) { this.key = Objects.requireNonNull(key); return this; }
    public UiTable<T> sizing(UiSizing sizing) { this.sizing = Objects.requireNonNull(sizing); return this; }
    public UiTable<T> column(Column<T> column) {
        if (columns.stream().anyMatch(value -> value.key().equals(column.key()))) throw new IllegalArgumentException("Duplicate table column");
        columns.add(column); return this;
    }
    public UiTable<T> rows(List<T> rows, Function<T, UiKey> key) {
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("Table needs paging or a bounded viewport above " + MAX_ROWS + " rows");
        this.rows = List.copyOf(rows); this.rowKey = Objects.requireNonNull(key); return this;
    }
    public UiTable<T> gaps(UiLength horizontal, UiLength vertical) { horizontalGap = Objects.requireNonNull(horizontal); verticalGap = Objects.requireNonNull(vertical); return this; }
    public UiTable<T> verticalAlignment(UiLayouts.GridAlignment alignment) { verticalAlignment = Objects.requireNonNull(alignment); return this; }
    /** Paging is feature-owned; layout discovers the actual capacity without changing the row objects. */
    public UiPagedTable paged(int page, java.util.function.IntConsumer selectPage) {
        var identities = new ArrayList<UiKey>();
        var table = build(identities);
        return new UiPagedTable(table, identities, page, selectPage);
    }
    public UiContainer build() { return build(new ArrayList<>()); }

    /** Factories read a loaded snapshot; single-line or fixed-size cells preserve the template's height. */
    public UiWindowedTable windowed(int count, T template, java.util.function.IntFunction<T> item, Function<T,UiKey> identity) {
        Objects.requireNonNull(item); Objects.requireNonNull(identity);
        var declaration = new UiTable<T>();
        declaration.columns.addAll(columns); declaration.key = key; declaration.sizing = sizing;
        declaration.horizontalGap = horizontalGap; declaration.verticalGap = verticalGap;
        declaration.verticalAlignment = verticalAlignment;
        var prototype = declaration.rows(List.of(template),identity).build();
        var captured = List.copyOf(columns);
        return new UiWindowedTable(prototype,count,index -> {
            T value = Objects.requireNonNull(item.apply(index),"table row");
            UiKey id = Objects.requireNonNull(identity.apply(value),"row key");
            if (id.equals(UiKey.AUTO)) throw new IllegalArgumentException("Window rows need stable domain keys");
            var cells = row(captured,value,id,0);
            return new UiContainer(id,prototype.layout(),cells,UiStyle.PLAIN,UiSizing.CONTENT);
        },0,_ -> {});
    }

    private UiContainer build(List<UiKey> identities) {
        if (columns.isEmpty()) throw new IllegalArgumentException("Table has no columns");
        List<UiChild> cells = new ArrayList<>();
        Set<UiKey> keys = new HashSet<>();
        for (int c = 0; c < columns.size(); c++) cells.add(UiChild.grid(UiKey.named("header:" + columns.get(c).key().value()), columns.get(c).header(), GridSlot.cell(0, c)));
        for (int r = 0; r < rows.size(); r++) {
            T row = rows.get(r); UiKey rowId = Objects.requireNonNull(rowKey.apply(row), "row key");
            if (!keys.add(rowId)) throw new IllegalArgumentException("Duplicate table row: " + rowId);
            identities.add(rowId);
            cells.addAll(row(columns,row,rowId,r + 1));
        }
        return new UiContainer(key, new UiTrackGridLayout(columns.stream().map(Column::track).toList(), horizontalGap, verticalGap, verticalAlignment),
                cells, UiStyle.PLAIN, sizing);
    }

    private static <T> List<UiChild> row(List<Column<T>> columns, T row, UiKey id, int index) {
        var cells = new ArrayList<UiChild>(columns.size());
        for (int column = 0; column < columns.size(); column++) {
            var definition = columns.get(column);
            String scope = "row[" + id.value().length() + ":" + id.value() + "]:" + definition.key().value();
            cells.add(UiChild.grid(UiKey.named(scope),Objects.requireNonNull(definition.content().apply(row)),GridSlot.cell(index,column)));
        }
        return List.copyOf(cells);
    }
}
