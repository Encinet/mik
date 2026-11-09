package org.encinet.mik.shared.menu.ui;

public record GridSlot(int row, int column, int rowSpan, int columnSpan) {
    public GridSlot {
        if (row < 0 || column < 0 || rowSpan < 1 || columnSpan < 1
                || (long) row + rowSpan > 4096 || (long) column + columnSpan > 4096) {
            throw new IllegalArgumentException("Invalid grid cell or span");
        }
    }

    public static GridSlot cell(int row, int column) { return new GridSlot(row, column, 1, 1); }
    public GridSlot withRowSpan(int span) { return new GridSlot(row, column, span, columnSpan); }
    public GridSlot withColumnSpan(int span) { return new GridSlot(row, column, rowSpan, span); }
}
