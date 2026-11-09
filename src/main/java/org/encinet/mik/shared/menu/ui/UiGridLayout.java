package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Shared grid topology, final-width measurement, span sizing and cell placement. */
final class UiGridLayout implements UiLayout {
    private final int columns;
    private final UiLength columnGap, rowGap;
    private final UiLayouts.GridAlignment horizontalAlignment, verticalAlignment;
    private final UiLayouts.GridSizing sizing;

    UiGridLayout(int columns, UiLength columnGap, UiLength rowGap,
                 UiLayouts.GridAlignment horizontalAlignment, UiLayouts.GridAlignment verticalAlignment) {
        this(columns, columnGap, rowGap, horizontalAlignment, verticalAlignment, UiLayouts.GridSizing.CONTENT);
    }

    UiGridLayout(int columns, UiLength columnGap, UiLength rowGap,
                 UiLayouts.GridAlignment horizontalAlignment, UiLayouts.GridAlignment verticalAlignment, UiLayouts.GridSizing sizing) {
        if (columns < 1 || columns > 4096) throw new IllegalArgumentException("Grid must have columns");
        this.columns = columns;
        this.columnGap = Objects.requireNonNull(columnGap);
        this.rowGap = Objects.requireNonNull(rowGap);
        this.horizontalAlignment = Objects.requireNonNull(horizontalAlignment);
        this.verticalAlignment = Objects.requireNonNull(verticalAlignment);
        this.sizing = Objects.requireNonNull(sizing);
    }

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        var cells = cells(scope);
        if (cells.isEmpty()) return new Result(limits.constrain(UiSize.ZERO), List.of());
        double available = Math.max(0, (limits.maxWidth() - columnGap.value() * (columns - 1)) / columns);
        var sizes = new ArrayList<UiSize>();
        // Width probes can wrap more than the final cells; enforce height after their widths settle.
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            sizes.add(scope.measure(index, UiConstraints.bounded(
                    available * cell.columnSpan() + columnGap.value() * (cell.columnSpan() - 1), UiSizing.CONTENT.maximum().height())));
        }
        return arrange(scope, limits, cells, tracks(cells, sizes, columns, columnGap.value(), true));
    }

    List<GridSlot> cells(MeasureScope scope) {
        var cells = new ArrayList<GridSlot>();
        int rows = 0;
        for (int index = 0; index < scope.count(); index++) {
            var cell = scope.gridSlot(index, columns);
            if (cell.column() + cell.columnSpan() > columns) throw new IllegalArgumentException("Grid cell exceeds columns");
            cells.add(cell);
            rows = Math.max(rows, cell.row() + cell.rowSpan());
        }
        if ((long) rows * columns > 65536) throw new IllegalArgumentException("Grid cell budget exceeded");
        var occupied = new boolean[rows][columns];
        for (var cell : cells) for (int row = cell.row(); row < cell.row() + cell.rowSpan(); row++)
            for (int column = cell.column(); column < cell.column() + cell.columnSpan(); column++) {
                if (occupied[row][column]) throw new IllegalArgumentException("Grid cells overlap");
                occupied[row][column] = true;
            }
        return cells;
    }

    /** Supplied column widths are authoritative; row heights use measurements at those widths. */
    Result arrange(MeasureScope scope, UiConstraints limits, List<GridSlot> cells, double[] widths) {
        if (cells.isEmpty()) return new Result(limits.constrain(UiSize.ZERO), List.of());
        double width = extent(widths, columnGap.value());
        if (width > limits.maxWidth() + 1e-6) throw new UiOverflowException("Grid columns exceed the viewport");
        var sizes = new ArrayList<UiSize>();
        int rows = 0;
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            sizes.add(scope.measure(index, allocation(scope, index,
                    span(widths, cell.column(), cell.columnSpan(), columnGap.value()), limits.maxHeight(), false)));
            rows = Math.max(rows, cell.row() + cell.rowSpan());
        }
        var heights = tracks(cells, sizes, rows, rowGap.value(), false);
        double height = extent(heights, rowGap.value());
        if (height > limits.maxHeight() + 1e-6) throw new UiOverflowException("Grid rows exceed the viewport");
        var positions = new ArrayList<Position>();
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            double cellWidth = span(widths, cell.column(), cell.columnSpan(), columnGap.value());
            double cellHeight = span(heights, cell.row(), cell.rowSpan(), rowGap.value());
            var size = verticalAlignment == UiLayouts.GridAlignment.STRETCH
                    ? scope.measure(index, allocation(scope, index, cellWidth, cellHeight, true)) : sizes.get(index);
            double spareWidth = cellWidth - size.width();
            double spareHeight = cellHeight - size.height();
            positions.add(new Position(before(widths, columnGap.value(), cell.column()) + offset(horizontalAlignment, spareWidth),
                    before(heights, rowGap.value(), cell.row()) + offset(verticalAlignment, spareHeight)));
        }
        return new Result(limits.constrain(new UiSize(width, height)), positions);
    }

    private UiConstraints allocation(MeasureScope scope, int index, double width, double height, boolean fillHeight) {
        var maximum = scope.maximum(index);
        double allocatedWidth = horizontalAlignment == UiLayouts.GridAlignment.STRETCH ? Math.min(width, maximum.width()) : width;
        double allocatedHeight = fillHeight ? Math.min(height, maximum.height()) : height;
        return new UiConstraints(horizontalAlignment == UiLayouts.GridAlignment.STRETCH ? allocatedWidth : 0, allocatedWidth,
                fillHeight ? allocatedHeight : 0, allocatedHeight);
    }

    private double[] tracks(List<GridSlot> cells, List<UiSize> sizes, int count, double gap, boolean horizontal) {
        var result = new double[count];
        // Single cells establish the basis; shorter spans precede longer ones. Geometry, rather
        // than declaration order, determines how a spanning cell shares any remaining demand.
        var order = IntStream.range(0, cells.size()).boxed().sorted(Comparator
                .comparingInt((Integer index) -> horizontal ? cells.get(index).columnSpan() : cells.get(index).rowSpan())
                .thenComparingInt(index -> horizontal ? cells.get(index).column() : cells.get(index).row())).toList();
        for (int index : order) {
            var cell = cells.get(index);
            int first = horizontal ? cell.column() : cell.row();
            int length = horizontal ? cell.columnSpan() : cell.rowSpan();
            double required = horizontal ? sizes.get(index).width() : sizes.get(index).height();
            double add = Math.max(0, required - span(result, first, length, gap)) / length;
            for (int track = first; track < first + length; track++) result[track] += add;
        }
        if (sizing == UiLayouts.GridSizing.UNIFORM) Arrays.fill(result, Arrays.stream(result).max().orElse(0));
        return result;
    }

    private static double offset(UiLayouts.GridAlignment alignment, double spare) {
        return switch (alignment) { case START -> 0; case CENTER, STRETCH -> Math.max(0, spare) / 2; case END -> Math.max(0, spare); };
    }

    private static double extent(double[] tracks, double gap) {
        return Arrays.stream(tracks).sum() + Math.max(0, tracks.length - 1) * gap;
    }

    private static double span(double[] tracks, int first, int length, double gap) {
        double size = gap * (length - 1);
        for (int index = first; index < first + length; index++) size += tracks[index];
        return size;
    }

    private static double before(double[] tracks, double gap, int index) {
        double result = gap * index;
        for (int track = 0; track < index; track++) result += tracks[track];
        return result;
    }
}
