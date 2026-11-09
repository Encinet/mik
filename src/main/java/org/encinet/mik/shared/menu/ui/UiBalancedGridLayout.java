package org.encinet.mik.shared.menu.ui;

import java.util.Objects;

/** Chooses measured arrangements with equal rectangular tracks and aligned control boundaries. */
record UiBalancedGridLayout(int maximumColumns, double targetAspectRatio, UiLayouts.GridBias bias,
                            UiLength columnGap, UiLength rowGap) implements UiLayout {
    UiBalancedGridLayout {
        if (maximumColumns < 1 || maximumColumns > UiTree.MAX_NODES)
            throw new IllegalArgumentException("Invalid maximum columns");
        if (!Double.isFinite(targetAspectRatio) || targetAspectRatio <= 0)
            throw new IllegalArgumentException("Grid aspect ratio must be finite and positive");
        Objects.requireNonNull(bias); Objects.requireNonNull(columnGap); Objects.requireNonNull(rowGap);
    }

    @Override public Result layout(MeasureScope scope, UiConstraints constraints) {
        var available = UiConstraints.bounded(constraints.maxWidth(), constraints.maxHeight());
        UiGridLayout chosen = null;
        double bestError = Double.POSITIVE_INFINITY, bestRatio = 0;
        UiOverflowException failure = null;
        int requiredColumns = 1;
        for (int index = 0; index < scope.count(); index++) {
            var cell = scope.gridSlot(index, maximumColumns);
            requiredColumns = Math.max(requiredColumns, cell.column() + cell.columnSpan());
        }
        int candidates = Math.min(maximumColumns, Math.max(scope.count(), requiredColumns));
        for (int columns = 1; columns <= candidates; columns++) {
            boolean supported = true;
            for (int index = 0; index < scope.count(); index++) {
                var cell = scope.gridSlot(index, columns);
                if (cell.column() + cell.columnSpan() > columns) { supported = false; break; }
            }
            if (!supported) continue;
            var grid = new UiGridLayout(columns, columnGap, rowGap, UiLayouts.GridAlignment.STRETCH,
                    UiLayouts.GridAlignment.STRETCH, UiLayouts.GridSizing.UNIFORM);
            Result candidate;
            try { candidate = grid.layout(scope, available); }
            catch (UiOverflowException overflow) { failure = overflow; continue; }
            var size = candidate.size();
            double ratio = size.height() == 0 || size.width() == 0 ? targetAspectRatio : size.width() / size.height();
            // Log distance treats reciprocal shapes equally; direction only breaks equivalent scores.
            double error = Math.abs(Math.log(ratio) - Math.log(targetAspectRatio));
            boolean preferred = bias == UiLayouts.GridBias.TALLER && ratio < bestRatio
                    || bias == UiLayouts.GridBias.WIDER && ratio > bestRatio;
            if (chosen == null || error < bestError - 1e-6 || Math.abs(error - bestError) <= 1e-6 && preferred) {
                chosen = grid; bestError = error; bestRatio = ratio;
            }
        }
        if (chosen == null) {
            if (failure != null) throw failure;
            throw new IllegalArgumentException("No grid topology supports the declared cells");
        }
        // Restore the selected measurements: the final candidate may have measured different cells.
        Result result = chosen.layout(scope, available);
        UiSize size = constraints.constrain(result.size());
        double left = (size.width() - result.size().width()) / 2;
        return new Result(size, result.positions().stream().map(position -> new Position(position.x() + left, position.y())).toList());
    }
}
