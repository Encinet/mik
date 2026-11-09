package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One centered reading item and optional edge groups; edges never change the centering reference. */
record UiCenteredBarLayout(UiKey center, UiKey leading, UiKey trailing, boolean shrinkCenter) implements UiLayout {
    UiCenteredBarLayout(UiKey center, UiKey leading, UiKey trailing) { this(center, leading, trailing, false); }
    private static final double GAP = 8, ROW_GAP = 6;

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        if (scope.count() == 0) return new Result(limits.constrain(UiSize.ZERO), List.of());
        int middle = -1, start = -1, end = -1;
        var available = UiConstraints.bounded(limits.maxWidth(), limits.maxHeight());
        var sizes = new ArrayList<UiSize>();
        for (int index = 0; index < scope.count(); index++) {
            if (scope.key(index).equals(center)) middle = index;
            else if (scope.key(index).equals(leading)) start = index;
            else if (scope.key(index).equals(trailing)) end = index;
            else throw new IllegalArgumentException("Unknown centered bar slot: " + scope.key(index).value());
            sizes.add(scope.measure(index, available));
        }
        if (middle < 0) throw new IllegalArgumentException("A centered bar needs its center item");
        var text = sizes.get(middle);
        var left = start < 0 ? UiSize.ZERO : sizes.get(start);
        var right = end < 0 ? UiSize.ZERO : sizes.get(end);
        boolean hasLeft = !left.equals(UiSize.ZERO), hasRight = !right.equals(UiSize.ZERO);
        boolean edges = hasLeft || hasRight;
        double side = Math.max(left.width(), right.width());
        double inlineWidth = text.width() + (edges ? 2 * (side + GAP) : 0);
        boolean inline = inlineWidth <= limits.maxWidth() + 1e-6;
        double centerWidth = limits.maxWidth() - (edges ? 2 * (side + GAP) : 0);
        if (!inline && shrinkCenter && centerWidth > 0) {
            try {
                text = scope.measure(middle, UiConstraints.bounded(centerWidth, limits.maxHeight()));
                inlineWidth = text.width() + (edges ? 2 * (side + GAP) : 0);
                inline = inlineWidth <= limits.maxWidth() + 1e-6;
            } catch (UiOverflowException overflow) {
                // Intrinsic, non-shrinkable center content keeps the ordinary stacked fallback.
            }
        }
        double edgeWidth = left.width() + right.width() + (hasLeft && hasRight ? GAP : 0);
        boolean sharedRow = edgeWidth <= limits.maxWidth() + 1e-6;
        double width = Math.clamp(inline ? inlineWidth : Math.max(text.width(), sharedRow ? edgeWidth : side),
                limits.minWidth(), limits.maxWidth());
        double edgeHeight = sharedRow ? Math.max(left.height(), right.height()) : left.height() + ROW_GAP + right.height();
        double height = inline ? Math.max(text.height(), Math.max(left.height(), right.height()))
                : text.height() + ROW_GAP + edgeHeight;
        if (height > limits.maxHeight() + 1e-6) throw new UiOverflowException("Centered content and edge tools exceed the viewport");
        var size = limits.constrain(new UiSize(width, height));
        var positions = new ArrayList<>(Collections.nCopies(scope.count(), new Position(0, 0)));
        positions.set(middle, new Position((size.width() - text.width()) / 2,
                inline ? (size.height() - text.height()) / 2 : 0));
        if (start >= 0) positions.set(start, new Position(0, inline ? (size.height() - left.height()) / 2
                : text.height() + ROW_GAP + (sharedRow ? (edgeHeight - left.height()) / 2 : 0)));
        if (end >= 0) positions.set(end, new Position(size.width() - right.width(), inline ? (size.height() - right.height()) / 2
                : text.height() + ROW_GAP + (sharedRow ? (edgeHeight - right.height()) / 2 : left.height() + ROW_GAP)));
        return new Result(size, positions);
    }
}
