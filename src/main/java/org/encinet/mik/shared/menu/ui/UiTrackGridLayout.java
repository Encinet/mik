package org.encinet.mik.shared.menu.ui;

import java.util.List;

/** Track allocation reuses the same grid span, overlap and row measurement algorithm. */
final class UiTrackGridLayout implements UiLayout {
    private final List<UiTrack> tracks;
    private final UiLength columnGap, rowGap;
    private final UiGridLayout grid;

    UiTrackGridLayout(List<UiTrack> tracks, UiLength columnGap, UiLength rowGap) {
        this(tracks, columnGap, rowGap, UiLayouts.GridAlignment.START);
    }

    UiTrackGridLayout(List<UiTrack> tracks, UiLength columnGap, UiLength rowGap, UiLayouts.GridAlignment verticalAlignment) {
        this.tracks = List.copyOf(tracks);
        if (tracks.isEmpty() || tracks.size() > 256) throw new IllegalArgumentException("Invalid track count");
        this.columnGap = java.util.Objects.requireNonNull(columnGap);
        this.rowGap = java.util.Objects.requireNonNull(rowGap);
        this.grid = new UiGridLayout(tracks.size(), columnGap, rowGap, UiLayouts.GridAlignment.START, verticalAlignment);
    }

    int columns() { return tracks.size(); }
    UiLength rowGap() { return rowGap; }
    double width(double[] columns) { return java.util.Arrays.stream(columns).sum() + columnGap.value() * (columns.length - 1); }

    UiLayout fixedColumns(double[] columns) {
        double[] widths = columns.clone();
        return (scope, limits) -> grid.arrange(scope, limits, grid.cells(scope), widths);
    }

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        var cells = grid.cells(scope);
        if (cells.isEmpty()) return new Result(limits.constrain(UiSize.ZERO), List.of());
        return grid.arrange(scope, limits, cells, columnWidths(scope, limits));
    }

    double[] columnWidths(MeasureScope scope, UiConstraints limits) {
        var cells = grid.cells(scope);
        int count = tracks.size();
        double available = limits.maxWidth() - columnGap.value() * (count - 1);
        double[] minimum = tracks.stream().mapToDouble(UiTrack::minimum).toArray();
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            if (cell.columnSpan() == 1) minimum[cell.column()] = Math.max(minimum[cell.column()], scope.minimum(index).width());
        }
        for (int column = 0; column < count; column++)
            if (minimum[column] > tracks.get(column).maximum()) throw new UiOverflowException("Cell minimum exceeds its column maximum");
        double[] natural = minimum.clone();
        // Content and intrinsic widths are shared across rows; fractional tracks start at their hard minima.
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            var track = tracks.get(cell.column());
            if (cell.columnSpan() == 1 && (track.kind() == UiTrack.Kind.CONTENT || track.kind() == UiTrack.Kind.INTRINSIC)) {
                UiSize size = scope.measure(index, UiConstraints.bounded(Math.min(track.maximum(), Math.max(0, available)), limits.maxHeight()));
                natural[cell.column()] = Math.max(natural[cell.column()], size.width());
            }
        }
        double[] widths = new double[count];
        boolean[] resolved = new boolean[count];
        // An arbitrary subtree can require more than its declared minimum. Discover each failing
        // flexible column once, reserve that minimum, and redistribute the remaining width.
        for (int pass = 0; pass <= count; pass++) {
            allocate(widths, natural, minimum, available);
            int narrow = -1;
            for (int column = 0; column < count; column++) {
                var kind = tracks.get(column).kind();
                if ((kind == UiTrack.Kind.CONTENT || kind == UiTrack.Kind.FRACTION) && !resolved[column]
                        && !fitsColumn(scope, column, widths[column], limits.maxHeight())) {
                    narrow = column;
                    break;
                }
            }
            if (narrow < 0) return widths;
            double upper = tracks.get(narrow).kind() == UiTrack.Kind.CONTENT ? natural[narrow]
                    : Math.min(tracks.get(narrow).maximum(), available);
            minimum[narrow] = minimumWidth(scope, narrow, widths[narrow], upper, limits.maxHeight());
            resolved[narrow] = true;
        }
        throw new UiOverflowException("Column minima did not stabilize");
    }

    private void allocate(double[] widths, double[] natural, double[] minimum, double available) {
        for (int column = 0; column < widths.length; column++) widths[column] = Math.max(natural[column], minimum[column]);
        double excess = java.util.Arrays.stream(widths).sum() - available;
        if (excess > 0) {
            double shrinkable = 0;
            for (int column = 0; column < widths.length; column++)
                if (tracks.get(column).kind() == UiTrack.Kind.CONTENT) shrinkable += widths[column] - minimum[column];
            if (excess > shrinkable + 1e-6) throw new UiOverflowException("Non-shrinkable columns exceed the viewport");
            if (shrinkable > 0) for (int column = 0; column < widths.length; column++)
                if (tracks.get(column).kind() == UiTrack.Kind.CONTENT)
                    widths[column] -= excess * (widths[column] - minimum[column]) / shrinkable;
        }
        double remainder = Math.max(0, available - java.util.Arrays.stream(widths).sum());
        boolean[] full = new boolean[widths.length];
        for (int pass = 0; pass < widths.length && remainder > 1e-6; pass++) {
            double weight = 0;
            for (int column = 0; column < widths.length; column++) if (!full[column]) weight += tracks.get(column).weight();
            if (weight == 0) break;
            double distributed = 0;
            for (int column = 0; column < widths.length; column++) if (!full[column] && tracks.get(column).weight() > 0) {
                double add = Math.min(remainder * tracks.get(column).weight() / weight, tracks.get(column).maximum() - widths[column]);
                widths[column] += add;
                distributed += add;
                full[column] = widths[column] >= tracks.get(column).maximum() - 1e-6;
            }
            remainder -= distributed;
        }
    }

    private double minimumWidth(MeasureScope scope, int column, double low, double high, double maxHeight) {
        if (!fitsColumn(scope, column, high, maxHeight)) throw new UiOverflowException("Column content exceeds width " + high + " or height " + maxHeight);
        for (int attempt = 0; attempt < 32 && high - low > 1e-6; attempt++) {
            double middle = (low + high) / 2;
            if (fitsColumn(scope, column, middle, maxHeight)) high = middle;
            else low = middle;
        }
        return high;
    }

    private boolean fitsColumn(MeasureScope scope, int column, double width, double maxHeight) {
        try {
            for (int i = 0; i < scope.count(); i++) {
                GridSlot cell = scope.gridSlot(i, tracks.size());
                if (cell.column() == column && cell.columnSpan() == 1)
                    scope.measure(i, UiConstraints.bounded(width, maxHeight));
            }
            return true;
        } catch (UiOverflowException overflow) { return false; }
    }
}
