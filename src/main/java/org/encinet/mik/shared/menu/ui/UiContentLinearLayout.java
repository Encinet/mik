package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.Collections;

/** Reserves persistent siblings, budgets the remaining axis for content, and publishes only its measured extent. */
final class UiContentLinearLayout implements UiLayout {
    private static final int MAX_CROSS_PASSES = 8;
    private final boolean horizontal;
    private final UiKey content;
    private final double gap;

    UiContentLinearLayout(boolean horizontal, UiKey content, UiLength gap) {
        this.horizontal = horizontal;
        this.content = java.util.Objects.requireNonNull(content);
        this.gap = gap.value();
    }

    private double main(UiSize size) { return horizontal ? size.width() : size.height(); }
    private double cross(UiSize size) { return horizontal ? size.height() : size.width(); }
    private UiSize size(double main, double cross) { return horizontal ? new UiSize(main,cross) : new UiSize(cross,main); }
    private UiConstraints constraints(double minimumCross, double maximumCross, double maximumMain) {
        return horizontal ? new UiConstraints(0,maximumMain,minimumCross,maximumCross)
                : new UiConstraints(minimumCross,maximumCross,0,maximumMain);
    }

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        int body = -1;
        var sizes = new UiSize[scope.count()];
        var naturalCross = new double[scope.count()];
        double mainLimit = horizontal ? limits.maxWidth() : limits.maxHeight();
        double crossLimit = horizontal ? limits.maxHeight() : limits.maxWidth();
        double minimumCross = horizontal ? limits.minHeight() : limits.minWidth();
        double fitCross = minimumCross;
        for (int index = 0; index < scope.count(); index++) {
            if (scope.key(index).equals(content)) { body = index; continue; }
            fitCross = Math.max(fitCross,cross(scope.minimum(index)));
            if (scope.linearSlot(index).crossSizing() != LinearSlot.CrossSizing.FIT) {
                sizes[index] = scope.measure(index,UiConstraints.bounded(limits.maxWidth(),limits.maxHeight()));
                naturalCross[index] = cross(sizes[index]);
                fitCross = Math.max(fitCross,naturalCross[index]);
                // Cross-axis stretching can reduce main-axis demand. Probe the widest
                // allocation before the content establishes the final shared cross extent.
                if (scope.linearSlot(index).crossSizing() == LinearSlot.CrossSizing.STRETCH)
                    sizes[index] = scope.measure(index,constraints(crossLimit,crossLimit,mainLimit));
            }
        }
        if (body < 0) throw new IllegalArgumentException("Missing content region: " + content);
        fitCross = Math.min(crossLimit,Math.max(fitCross,cross(scope.minimum(body))));
        for (int index = 0; index < sizes.length; index++) if (index != body && sizes[index] == null) {
            sizes[index] = scope.measure(index,constraints(0,fitCross,mainLimit));
            naturalCross[index] = cross(sizes[index]);
        }
        double bodyCross = 0;
        for (int pass = 0; pass < MAX_CROSS_PASSES; pass++) {
            double chromeMain = 0;
            int visible = 0;
            for (int index = 0; index < sizes.length; index++) if (index != body) {
                chromeMain += main(sizes[index]);
                if (main(sizes[index]) > 0) visible++;
            }
            if (chromeMain + gap * Math.max(0,visible - 1) > mainLimit + 1e-6)
                throw new UiOverflowException("Persistent page controls exceed the viewport");
            double available = Math.max(0,mainLimit - chromeMain - gap * visible);
            sizes[body] = scope.measure(body,constraints(bodyCross,crossLimit,available));
            double crossExtent = minimumCross;
            for (int index = 0; index < sizes.length; index++)
                crossExtent = Math.max(crossExtent,index == body ? cross(sizes[index]) : naturalCross[index]);
            boolean changed = false;
            for (int index = 0; index < sizes.length; index++) if (index != body
                    && scope.linearSlot(index).crossSizing() != LinearSlot.CrossSizing.CONTENT) {
                double target = Math.min(crossExtent,cross(scope.maximum(index)));
                boolean stretch = scope.linearSlot(index).crossSizing() == LinearSlot.CrossSizing.STRETCH;
                var measured = scope.measure(index,constraints(stretch ? target : 0,target,mainLimit));
                changed |= !measured.equals(sizes[index]);
                sizes[index] = measured;
                if (!stretch) naturalCross[index] = cross(measured);
            }
            if (scope.linearSlot(body).crossSizing() == LinearSlot.CrossSizing.STRETCH) {
                double target = Math.min(crossExtent,cross(scope.maximum(body)));
                changed |= Math.abs(target - bodyCross) > 1e-6;
                bodyCross = target;
            }
            if (changed) continue;
            if (main(sizes[body]) > 0) visible++;
            var size = limits.constrain(size(chromeMain + main(sizes[body]) + gap * Math.max(0,visible - 1),crossExtent));
            var positions = new ArrayList<>(Collections.nCopies(scope.count(),new Position(0,0)));
            double cursor = 0;
            boolean previous = false;
            for (int index = 0; index < sizes.length; index++) {
                if (main(sizes[index]) > 0 && previous) cursor += gap;
                double spare = cross(size) - cross(sizes[index]);
                double offset = switch (scope.linearSlot(index).alignment()) {
                    case START -> 0;
                    case CENTER -> spare / 2;
                    case END -> spare;
                };
                positions.set(index,horizontal ? new Position(cursor,offset) : new Position(offset,cursor));
                cursor += main(sizes[index]);
                previous |= main(sizes[index]) > 0;
            }
            return new Result(size,positions);
        }
        throw new UiOverflowException("Content layout cross axis did not stabilize");
    }
}
