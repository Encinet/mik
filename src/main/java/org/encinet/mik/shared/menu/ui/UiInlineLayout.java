package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Whole inline units wrap in declaration order; each line shares one baseline and bounded height. */
final class UiInlineLayout implements UiLayout {
    private final double gap;
    private final double lineGap;
    private final boolean wrap;
    private final UiBaseline.Line line;
    private final LinearSlot.Alignment fallback;

    UiInlineLayout(UiLength gap, UiLength lineGap, boolean wrap, UiBaseline.Line line, LinearSlot.Alignment fallback) {
        this.gap = gap.value(); this.lineGap = lineGap.value(); this.wrap = wrap;
        this.line = Objects.requireNonNull(line); this.fallback = Objects.requireNonNull(fallback);
    }

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        List<Position> positions = new ArrayList<>(scope.count());
        List<UiSize> sizes = new ArrayList<>(scope.count());
        List<Double> baselines = new ArrayList<>(scope.count());
        double width = 0, top = 0, lineWidth = 0, ascent = 0, descent = 0;
        int first = 0, visible = 0;
        for (int index = 0; index < scope.count(); index++) {
            if (scope.linearSlot(index).weight() > 0) throw new IllegalArgumentException("Inline units cannot use flex weights");
            UiSize size = scope.measure(index, UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()));
            double baseline = scope.measuredBaseline(index).map(value -> value.at(line)).orElseGet(() -> switch (fallback) {
                case START -> 0.0;
                case CENTER -> size.height() / 2;
                case END -> size.height();
            });
            if ((size.width() > 0 || size.height() > 0) && visible > 0 && lineWidth + gap + size.width() > limits.maxWidth() + 1e-6) {
                if (!wrap) throw new UiOverflowException("Inline row exceeds its width; wrap or stack its units");
                arrange(positions, sizes, baselines, first, index, top, ascent);
                width = Math.max(width, lineWidth);
                top += ascent + descent + lineGap;
                first = index; visible = 0; lineWidth = 0; ascent = 0; descent = 0;
            }
            boolean nonempty = size.width() > 0 || size.height() > 0;
            if (nonempty && visible > 0) lineWidth += gap;
            positions.add(new Position(lineWidth, top));
            sizes.add(size); baselines.add(baseline);
            if (nonempty) {
                lineWidth += size.width(); visible++;
                ascent = Math.max(ascent, baseline);
                descent = Math.max(descent, size.height() - baseline);
            }
        }
        arrange(positions, sizes, baselines, first, scope.count(), top, ascent);
        double height = top + ascent + descent;
        width = Math.max(width, lineWidth);
        if (width > limits.maxWidth() + 1e-6 || height > limits.maxHeight() + 1e-6)
            throw new UiOverflowException("Inline lines exceed their available bounds");
        return new Result(limits.constrain(new UiSize(width, height)), positions);
    }

    private static void arrange(List<Position> positions, List<UiSize> sizes, List<Double> baselines,
                                int first, int end, double top, double ascent) {
        for (int index = first; index < end; index++) {
            Position previous = positions.get(index);
            positions.set(index, new Position(previous.x(), sizes.get(index).height() == 0 ? top : top + ascent - baselines.get(index)));
        }
    }
}
