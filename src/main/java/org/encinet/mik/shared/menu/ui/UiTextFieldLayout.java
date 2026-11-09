package org.encinet.mik.shared.menu.ui;

import java.util.List;

/** The optional tail is inside the field's width budget; its absence does not move the value. */
final class UiTextFieldLayout implements UiLayout {
    private static final double PREFERRED = 240, MIN_HEIGHT = 22, GAP = 6, MIN_READING = 120;

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        if (scope.count() < 1 || scope.count() > 2) throw new IllegalArgumentException("A field needs a value and optional tools");
        UiSize tools = scope.count() == 2 ? scope.measure(1, UiConstraints.bounded(limits.maxWidth(), limits.maxHeight())) : UiSize.ZERO;
        double tail = tools.width() > 0 ? GAP + tools.width() : 0;
        double width = Math.clamp(Math.max(PREFERRED, MIN_READING + tail), limits.minWidth(), limits.maxWidth());
        double reading = width - tail;
        if (reading < MIN_READING - 1e-6) {
            if (scope.count() == 2) return new UiActionRowLayout(UiLength.ui(PREFERRED)).layout(scope, limits);
            throw new UiOverflowException("Field has no usable reading region");
        }
        reading = Math.max(MIN_READING, reading);
        var value = scope.measure(0, new UiConstraints(reading, reading, 0, limits.maxHeight()));
        double height = Math.max(MIN_HEIGHT, Math.max(value.height(), tools.height()));
        if (height > limits.maxHeight() + 1e-6) throw new UiOverflowException("Field exceeds its height budget");
        return new Result(limits.constrain(new UiSize(width, height)), scope.count() == 1
                ? List.of(new Position(0, (height-value.height())/2))
                : List.of(new Position(0, (height-value.height())/2), new Position(width-tools.width(), (height-tools.height())/2)));
    }
}
