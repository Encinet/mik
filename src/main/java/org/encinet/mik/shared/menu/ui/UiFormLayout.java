package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Measures labels together. The choice of columns depends on labels and control minima, never draft length. */
final class UiFormLayout implements UiLayout {
    record Row(int label, int control) { }
    private static final double COLUMN_GAP = 10, LABEL_GAP = 4, ROW_GAP = 8;
    private final List<Row> rows;

    UiFormLayout(List<Row> rows) { this.rows = List.copyOf(rows); }

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        double labelWidth = 0, controlWidth = 0, minimumControl = 0, unlabelledWidth = 0;
        var available = UiConstraints.bounded(limits.maxWidth(), 4096);
        for (var row : rows) {
            UiSize control = scope.measure(row.control(), available);
            if (row.label() >= 0) {
                labelWidth = Math.max(labelWidth, scope.measure(row.label(), available).width());
                controlWidth = Math.max(controlWidth, control.width());
                minimumControl = Math.max(minimumControl, scope.minimum(row.control()).width());
            } else unlabelledWidth = Math.max(unlabelledWidth, control.width());
        }
        boolean columns = labelWidth > 0 && labelWidth + COLUMN_GAP + minimumControl <= limits.maxWidth();
        if (columns) {
            try { return arrange(scope, limits, labelWidth, controlWidth, unlabelledWidth, true); }
            catch (UiOverflowException ignored) { /* An arbitrary control subtree may have a larger intrinsic minimum. */ }
        }
        return arrange(scope, limits, labelWidth, controlWidth, unlabelledWidth, false);
    }

    private Result arrange(MeasureScope scope, UiConstraints limits, double labelWidth, double controlWidth,
                           double unlabelledWidth, boolean columns) {
        double naturalWidth = columns ? Math.max(unlabelledWidth, labelWidth + COLUMN_GAP + controlWidth)
                : Math.max(labelWidth, Math.max(controlWidth, unlabelledWidth));
        double width = Math.clamp(naturalWidth, limits.minWidth(), limits.maxWidth());
        List<Position> positions = new ArrayList<>(Collections.nCopies(scope.count(), null));
        double y = 0;
        for (var row : rows) {
            double rowHeight = 0;
            if (row.label() >= 0) {
                UiSize label = scope.measure(row.label(), UiConstraints.bounded(columns ? labelWidth : width, limits.maxHeight()));
                positions.set(row.label(), new Position(0, y));
                if (columns) rowHeight = label.height();
                else y += label.height() + LABEL_GAP;
            }
            double x = columns && row.label() >= 0 ? labelWidth + COLUMN_GAP : 0;
            double allocation = width - x;
            UiSize control = scope.measure(row.control(), new UiConstraints(
                    Math.min(allocation, scope.maximum(row.control()).width()), allocation, 0, limits.maxHeight()));
            positions.set(row.control(), new Position(x, y));
            y += Math.max(rowHeight, control.height()) + ROW_GAP;
        }
        double height = Math.max(0, y - ROW_GAP);
        if (height > limits.maxHeight() + 1e-6)
            throw new UiOverflowException("Form fields need " + new UiSize(width, height) + " within " + limits);
        return new Result(limits.constrain(new UiSize(width, height)), positions);
    }
}
