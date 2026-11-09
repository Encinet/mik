package org.encinet.mik.shared.menu.ui;

import java.util.List;

/** A persistent header keeps its natural height; the reading body receives the remaining budget. */
final class UiHeaderBodyLayout implements UiLayout {
    private static final int MAX_WIDTH_PASSES = 8;
    private final double gap;
    UiHeaderBodyLayout(UiLength gap) { this.gap = gap.value(); }

    @Override public Result layout(MeasureScope scope, UiConstraints constraints) {
        if (scope.count() != 2) throw new IllegalArgumentException("A header/body layout needs two regions");
        var natural = UiConstraints.bounded(constraints.maxWidth(), constraints.maxHeight());
        var header = scope.measure(0, natural);
        var body = scope.measure(1, UiConstraints.bounded(constraints.maxWidth(),
                Math.max(0, constraints.maxHeight() - header.height() - (header.height() > 0 ? gap : 0))));
        double width = Math.max(constraints.minWidth(), Math.max(header.width(), body.width()));
        for (int pass = 0; pass < MAX_WIDTH_PASSES; pass++) {
            // Stretch within the header's own bounds, then budget the body from its final height.
            double headerWidth = Math.min(width, scope.maximum(0).width());
            header = scope.measure(0, new UiConstraints(headerWidth, headerWidth, 0, constraints.maxHeight()));
            double available = constraints.maxHeight() - header.height();
            double separator = header.height() > 0 ? gap : 0;
            body = scope.measure(1, UiConstraints.bounded(constraints.maxWidth(), Math.max(0, available - separator)));
            if (body.height() == 0) separator = 0;
            double requiredWidth = Math.max(header.width(), body.width());
            if (requiredWidth <= width + 1e-6) {
                var size = constraints.constrain(new UiSize(width, header.height() + separator + body.height()));
                return new Result(size, List.of(new Position((size.width() - header.width()) / 2, 0),
                        new Position((size.width() - body.width()) / 2, header.height() + separator)));
            }
            // A newly visible pager can widen the body. Only this local width grows during remeasurement.
            width = requiredWidth;
        }
        throw new UiOverflowException("Header/body width did not stabilize");
    }
}
