package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.List;

/** Layouts arrange a complete sibling group once and share its track measurements. */
public final class UiLayouts {
    private UiLayouts() { }

    /** Preferred direction for a composition that can reflow as one group. */
    public enum Axis { HORIZONTAL, VERTICAL }

    /** Placement or filling of content within its shared grid track or span. */
    public enum GridAlignment { START, CENTER, END, STRETCH }

    /** Content-sized tracks or equal tracks within each axis; width and height remain independent. */
    public enum GridSizing { CONTENT, UNIFORM }

    /** Preferred direction when measured grid shapes are equally close to their target ratio. */
    public enum GridBias { NONE, TALLER, WIDER }

    /** Content receives the finite height left after headers and tools; the resulting page stays content-sized. */
    public static UiLayout contentColumn(UiKey content, UiLength gap) {
        return new UiContentLinearLayout(false, content, gap);
    }

    /** Content receives the finite width left after side regions; unused width does not become an empty slot. */
    public static UiLayout contentRow(UiKey content, UiLength gap) {
        return new UiContentLinearLayout(true, content, gap);
    }

    /** Compare measured bounds against a width/height target, with equal rectangular cells and preserved content scale. */
    public static UiLayout balancedGrid(int maximumColumns, double targetAspectRatio, GridBias bias,
                                         UiLength columnGap, UiLength rowGap) {
        return new UiBalancedGridLayout(maximumColumns, targetAspectRatio, bias, columnGap, rowGap);
    }

    public static UiLayout row(UiLength gap) { return new UiLinearLayout(true, gap); }
    public static UiLayout column(UiLength gap) { return new UiLinearLayout(false, gap); }
    public static UiLayout headerBody(UiLength gap) { return new UiHeaderBodyLayout(gap); }

    public static UiLayout baselineRow(UiLength gap, UiBaseline.Line line, LinearSlot.Alignment fallback) {
        return new UiInlineLayout(gap, UiLength.ZERO, false, line, fallback);
    }

    public static UiLayout inline(UiLength gap, UiLength lineGap, UiBaseline.Line line, LinearSlot.Alignment fallback) {
        return new UiInlineLayout(gap, lineGap, true, line, fallback);
    }

    /** A natural reading width, independent of text length; a host can shrink or stretch it. */
    public static UiLayout preferredWidth(UiLength width, UiLayout content) {
        java.util.Objects.requireNonNull(width);
        java.util.Objects.requireNonNull(content);
        return (scope, limits) -> {
            double target = Math.clamp(width.value(), limits.minWidth(), limits.maxWidth());
            return content.layout(scope, new UiConstraints(target, target, limits.minHeight(), limits.maxHeight()));
        };
    }

    /** Try complete arrangements in order. Only a real size overflow selects the next layout. */
    public static UiLayout firstFit(UiLayout... alternatives) {
        List<UiLayout> layouts = List.of(alternatives);
        if (layouts.isEmpty()) throw new IllegalArgumentException("At least one layout is required");
        return (scope, constraints) -> {
            UiOverflowException failure = null;
            for (UiLayout layout : layouts) {
                try { return layout.layout(scope, constraints); }
                catch (UiOverflowException error) { failure = error; }
            }
            throw java.util.Objects.requireNonNull(failure);
        };
    }

    /** Free 2D composition with child centers in a bounded logical viewport. */
    public static UiLayout canvas(UiSize viewport, java.util.Map<UiKey, UiLayout.Position> centers) {
        java.util.Map<UiKey, UiLayout.Position> positions = java.util.Map.copyOf(centers);
        return (scope, limits) -> {
            if (viewport.width() > limits.maxWidth() || viewport.height() > limits.maxHeight())
                throw new UiOverflowException("Canvas viewport exceeds its constraints");
            java.util.List<UiLayout.Position> result = new java.util.ArrayList<>();
            for (int i = 0; i < scope.count(); i++) {
                UiLayout.Position center = positions.get(scope.key(i));
                if (center == null) throw new IllegalArgumentException("Missing canvas placement: " + scope.key(i));
                double width = 2 * Math.min(center.x(), viewport.width() - center.x());
                double height = 2 * Math.min(center.y(), viewport.height() - center.y());
                if (width < 0 || height < 0) throw new IllegalArgumentException("Canvas center is outside the viewport");
                UiSize size = scope.measure(i, UiConstraints.bounded(width, height));
                result.add(new UiLayout.Position(center.x() - size.width() / 2, center.y() - size.height() / 2));
            }
            return new UiLayout.Result(limits.constrain(viewport), result);
        };
    }

    public static UiLayout stack() {
        return (scope, limits) -> {
            List<UiSize> sizes = new ArrayList<>();
            double width = 0, height = 0;
            for (int i = 0; i < scope.count(); i++) {
                UiSize size = scope.measure(i, UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()));
                sizes.add(size);
                width = Math.max(width, size.width()); height = Math.max(height, size.height());
            }
            UiSize size = limits.constrain(new UiSize(width, height));
            return new UiLayout.Result(size, sizes.stream().map(child ->
                    new UiLayout.Position((size.width() - child.width()) / 2, (size.height() - child.height()) / 2)).toList());
        };
    }

    /** Natural-width rows, with each row's children centered vertically like an ordinary row. */
    public static UiLayout wrap(UiLength gap, UiLength rowGap) {
        return (scope, limits) -> {
            List<UiLayout.Position> positions = new ArrayList<>();
            List<UiSize> sizes = new ArrayList<>();
            int rowStart = 0;
            double x = 0, y = 0, rowHeight = 0, width = 0;
            for (int i = 0; i < scope.count(); i++) {
                UiSize size = scope.measure(i, UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()));
                if (size.equals(UiSize.ZERO)) {
                    sizes.add(size);
                    positions.add(new UiLayout.Position(0, 0));
                    continue;
                }
                if (x > 0 && x + size.width() > limits.maxWidth()) {
                    centerWrappedRow(positions, sizes, rowStart, rowHeight);
                    rowStart = i;
                    x = 0; y += rowHeight + rowGap.value(); rowHeight = 0;
                }
                sizes.add(size);
                positions.add(new UiLayout.Position(x, y));
                width = Math.max(width, x + size.width()); rowHeight = Math.max(rowHeight, size.height());
                x += size.width() + gap.value();
            }
            centerWrappedRow(positions, sizes, rowStart, rowHeight);
            if (y + rowHeight > limits.maxHeight() + 1e-6) throw new UiOverflowException("Wrapped rows exceed the viewport");
            return new UiLayout.Result(limits.constrain(new UiSize(width, y + rowHeight)), positions);
        };
    }

    /** Natural-width rows while they stay compact, then a caller-selected arrangement. */
    public static UiLayout adaptiveWrap(int maximumRows, UiLength gap, UiLength rowGap, UiLayout fallback) {
        if (maximumRows < 1) throw new IllegalArgumentException("Invalid maximum wrapped rows");
        java.util.Objects.requireNonNull(gap, "gap");
        java.util.Objects.requireNonNull(rowGap, "rowGap");
        java.util.Objects.requireNonNull(fallback, "fallback");
        UiLayout flow = wrap(gap, rowGap);
        return (scope, limits) -> {
            try {
                UiLayout.Result result = flow.layout(scope, limits);
                if (rowCount(result, scope) <= maximumRows) return result;
            } catch (UiOverflowException ignored) {
                // A compact fallback may preserve access to controls when the natural flow is too tall.
            }
            return fallback.layout(scope, limits);
        };
    }

    private static int rowCount(UiLayout.Result layout, UiLayout.MeasureScope scope) {
        var order = new ArrayList<Integer>();
        for (int index = 0; index < scope.count(); index++)
            if (!scope.measuredSize(index).equals(UiSize.ZERO)) order.add(index);
        order.sort(java.util.Comparator.comparingDouble(index -> layout.positions().get(index).y()));
        int rows = 0;
        double rowBottom = Double.NEGATIVE_INFINITY;
        for (int index : order) {
            UiLayout.Position position = layout.positions().get(index);
            double bottom = position.y() + scope.measuredSize(index).height();
            if (rows == 0 || position.y() >= rowBottom - 1e-6) {
                rows++;
                rowBottom = bottom;
            } else {
                rowBottom = Math.max(rowBottom, bottom);
            }
        }
        return rows;
    }

    private static void centerWrappedRow(List<UiLayout.Position> positions, List<UiSize> sizes, int start, double height) {
        for (int i = start; i < positions.size(); i++) {
            var position = positions.get(i);
            positions.set(i, new UiLayout.Position(position.x(), position.y() + (height - sizes.get(i).height()) / 2));
        }
    }

    public static UiLayout grid(int columns, UiLength columnGap, UiLength rowGap) {
        return grid(columns, columnGap, rowGap, GridAlignment.START);
    }

    /** Content, intrinsic and flexible tracks share column widths across every row. */
    public static UiLayout grid(List<UiTrack> tracks, UiLength columnGap, UiLength rowGap) {
        return new UiTrackGridLayout(tracks, columnGap, rowGap);
    }

    public static UiLayout grid(int columns, UiLength columnGap, UiLength rowGap, GridAlignment alignment) {
        return grid(columns, columnGap, rowGap, alignment, GridAlignment.START);
    }

    public static UiLayout grid(int columns, UiLength columnGap, UiLength rowGap,
                                GridAlignment horizontalAlignment, GridAlignment verticalAlignment) {
        return grid(columns, columnGap, rowGap, horizontalAlignment, verticalAlignment, GridSizing.CONTENT);
    }

    public static UiLayout grid(int columns, UiLength columnGap, UiLength rowGap,
                                GridAlignment horizontalAlignment, GridAlignment verticalAlignment, GridSizing sizing) {
        return new UiGridLayout(columns, columnGap, rowGap, horizontalAlignment, verticalAlignment, sizing);
    }

    public static UiLayout adaptiveGrid(int maximumColumns, UiLength columnGap, UiLength rowGap) {
        return adaptiveGrid(maximumColumns, columnGap, rowGap, GridAlignment.START);
    }

    public static UiLayout adaptiveGrid(int maximumColumns, UiLength columnGap, UiLength rowGap, GridAlignment alignment) {
        if (maximumColumns < 1) throw new IllegalArgumentException("Invalid maximum columns");
        java.util.Objects.requireNonNull(alignment, "alignment");
        return (scope, constraints) -> {
            UiOverflowException failure = null;
            for (int columns = Math.min(maximumColumns, Math.max(1, scope.count())); columns > 0; columns--) {
                try { return grid(columns, columnGap, rowGap, alignment).layout(scope, constraints); }
                catch (UiOverflowException error) { failure = error; }
            }
            throw java.util.Objects.requireNonNull(failure);
        };
    }
}
