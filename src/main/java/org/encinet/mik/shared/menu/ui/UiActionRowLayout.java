package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A flexible reading region and an intact trailing action group, stacked when their minima do not fit. */
final class UiActionRowLayout implements UiLayout {
    private static final double GAP = 10, ROW_GAP = 6;
    private final double preferredWidth;

    UiActionRowLayout() { preferredWidth = 0; }
    /** Keep a value field's reading width independent of its text, while accommodating its local tools. */
    UiActionRowLayout(UiLength preferredWidth) { this.preferredWidth = preferredWidth.value(); }

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        if (scope.count() == 0) return new Result(limits.constrain(UiSize.ZERO), List.of());
        if (scope.count() > 2) throw new IllegalArgumentException("An action row has at most two regions");
        // Probe natural widths before assigning the real height budget. At its preferred
        // width a message may wrap more than it will in the final, stretched reading region.
        var available = UiConstraints.bounded(limits.maxWidth(), 4096);
        var leading = scope.measure(0, available);
        if (scope.count() == 1) return single(scope, limits, 0, leading);
        var actions = scope.measure(1, available);
        if (actions.equals(UiSize.ZERO)) return single(scope, limits, 0, leading);
        if (leading.equals(UiSize.ZERO)) return single(scope, limits, 1, actions);
        double minimumContent = Math.max(scope.minimum(0).width(), Math.min(leading.width(), 120));
        limits = preferred(limits, minimumContent + GAP + actions.width());
        if (minimumContent + GAP + actions.width() <= limits.maxWidth() + 1e-6) {
            try { return arrange(scope, limits, leading, actions, true); }
            catch (UiOverflowException ignored) { /* Preserve a nested control's own constraints by stacking the regions. */ }
        }
        return arrange(scope, limits, leading, actions, false);
    }

    private Result single(MeasureScope scope, UiConstraints limits, int index, UiSize content) {
        limits = preferred(limits, 0);
        var size = limits.constrain(content);
        boolean trailing = scope.linearSlot(index).weight() == 0;
        if (!trailing) content = scope.measure(index, new UiConstraints(
                Math.min(size.width(), scope.maximum(index).width()), size.width(), 0, limits.maxHeight()));
        if (content.height() > limits.maxHeight() + 1e-6) throw new UiOverflowException("Action row exceeds the viewport");
        size = limits.constrain(new UiSize(size.width(), content.height()));
        var positions = new ArrayList<>(Collections.nCopies(scope.count(), new Position(0, 0)));
        positions.set(index, new Position(trailing ? size.width() - content.width() : 0, 0));
        return new Result(size, positions);
    }

    private UiConstraints preferred(UiConstraints limits, double minimumInline) {
        if (preferredWidth == 0) return limits;
        double width = Math.clamp(Math.max(preferredWidth, minimumInline), limits.minWidth(), limits.maxWidth());
        return new UiConstraints(width, width, limits.minHeight(), limits.maxHeight());
    }

    private Result arrange(MeasureScope scope, UiConstraints limits, UiSize leading, UiSize actions, boolean inline) {
        double naturalWidth = inline ? leading.width() + GAP + actions.width() : Math.max(leading.width(), actions.width());
        double width = Math.clamp(naturalWidth, limits.minWidth(), limits.maxWidth());
        // Repeated measurement can differ by a fraction of a UI unit. Keep the
        // declared minimum when subtraction only loses floating-point precision.
        double contentWidth = inline ? Math.max(scope.minimum(0).width(), width - GAP - actions.width()) : width;
        leading = scope.measure(0, new UiConstraints(Math.min(contentWidth, scope.maximum(0).width()), contentWidth,
                0, limits.maxHeight()));
        if (!inline) actions = scope.measure(1, UiConstraints.bounded(width, limits.maxHeight()));
        double height = inline ? Math.max(leading.height(), actions.height()) : leading.height() + ROW_GAP + actions.height();
        if (height > limits.maxHeight() + 1e-6) throw new UiOverflowException("Action row exceeds the viewport");
        return new Result(limits.constrain(new UiSize(width, height)), List.of(
                new Position(0, inline ? (height - leading.height()) / 2 : 0),
                new Position(width - actions.width(), inline ? (height - actions.height()) / 2 : leading.height() + ROW_GAP)));
    }
}
