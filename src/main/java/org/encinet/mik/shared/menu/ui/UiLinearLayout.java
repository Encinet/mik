package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.List;

/** Intrinsic cross-axis measurement, dependent content, bounded flex allocation and placement. */
final class UiLinearLayout implements UiLayout {
    private final boolean horizontal;
    private final double gap;

    UiLinearLayout(boolean horizontal, UiLength gap) {
        this.horizontal = horizontal;
        this.gap = gap.value();
    }

    private double main(UiSize size) { return horizontal ? size.width() : size.height(); }
    private double cross(UiSize size) { return horizontal ? size.height() : size.width(); }

    private UiConstraints childConstraints(double main, double cross, boolean stretch) {
        return horizontal ? new UiConstraints(0, main, stretch ? cross : 0, cross)
                : new UiConstraints(stretch ? cross : 0, cross, 0, main);
    }

    @Override public Result layout(MeasureScope scope, UiConstraints limits) {
        int count = scope.count();
        if (count == 0) return new Result(limits.constrain(UiSize.ZERO), List.of());
        double mainLimit = horizontal ? limits.maxWidth() : limits.maxHeight();
        double[] allocation = new double[count];
        UiSize[] sizes = new UiSize[count];
        double minimum = 0, basis = 0, weight = 0;
        double crossLimit = horizontal ? limits.maxHeight() : limits.maxWidth();
        double sharedCross = horizontal ? limits.minHeight() : limits.minWidth();
        boolean crossKnown = sharedCross > 0;
        for (int i = 0; i < count; i++) {
            LinearSlot slot = scope.linearSlot(i);
            double min = main(scope.minimum(i));
            minimum += min;
            sharedCross = Math.max(sharedCross, cross(scope.minimum(i)));
            if (slot.crossSizing() != LinearSlot.CrossSizing.FIT) {
                sizes[i] = scope.measure(i, UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()));
                sharedCross = Math.max(sharedCross, cross(sizes[i]));
                crossKnown |= cross(sizes[i]) > 0;
            }
            if (slot.weight() == 0 && sizes[i] != null) {
                allocation[i] = main(sizes[i]);
            } else {
                allocation[i] = min;
                weight += slot.weight();
            }
            basis += allocation[i];
        }
        // Accessories are measured only after intrinsic siblings establish the cross axis.
        // With no source, the finite parent bounds let a lone accessory retain its natural size.
        if (sharedCross > crossLimit + 1e-6) throw new UiOverflowException("Linear cross-axis minimum exceeds the viewport");
        if (!crossKnown) sharedCross = crossLimit;
        for (int i = 0; i < count; i++) if (scope.linearSlot(i).weight() == 0 && sizes[i] == null) {
            sizes[i] = scope.measure(i, childConstraints(mainLimit, sharedCross, false));
            basis += main(sizes[i]) - allocation[i];
            allocation[i] = main(sizes[i]);
        }
        // Resolve shared cross-axis sizes before assigning main-axis budgets. Text wrapping
        // and responsive subtrees can change their main-axis demand when stretched.
        if (weight == 0) {
            for (int i = 0; i < count; i++) if (scope.linearSlot(i).crossSizing() == LinearSlot.CrossSizing.STRETCH) {
                double target = Math.min(sharedCross, cross(scope.maximum(i)));
                sizes[i] = scope.measure(i, childConstraints(mainLimit, target, true));
                basis += main(sizes[i]) - allocation[i];
                allocation[i] = main(sizes[i]);
            }
        }
        // Nonvisual input declarations remain in the tree without creating a visible gap.
        boolean[] spaced = new boolean[count];
        int visible = 0;
        for (int i = 0; i < count; i++) {
            spaced[i] = allocation[i] > 0 || scope.linearSlot(i).weight() > 0;
            if (spaced[i]) visible++;
        }
        double gaps = gap * Math.max(0, visible - 1);
        minimum += gaps;
        basis += gaps;
        if (minimum > mainLimit + 1e-6) throw new UiOverflowException("Linear minimum and gaps exceed the viewport");
        if (basis > mainLimit + 1e-6) {
            double shrinkable = basis - minimum;
            for (int i = 0; i < count; i++) {
                double room = allocation[i] - main(scope.minimum(i));
                allocation[i] -= (basis - mainLimit) * room / shrinkable;
            }
        }
        double remaining = Math.max(0, mainLimit - gaps - java.util.Arrays.stream(allocation).sum());
        if (weight > 0) for (int i = 0; i < count; i++) allocation[i] += remaining * scope.linearSlot(i).weight() / weight;
        double extent = gaps, crossExtent = 0;
        for (int i = 0; i < count; i++) {
            if (sizes[i] == null || allocation[i] != main(sizes[i])) {
                double crossBound = scope.linearSlot(i).crossSizing() == LinearSlot.CrossSizing.FIT ? sharedCross : crossLimit;
                UiConstraints available = childConstraints(allocation[i], crossBound, false);
                if (weight == 0 && scope.linearSlot(i).crossSizing() == LinearSlot.CrossSizing.STRETCH) {
                    double target = Math.min(sharedCross, cross(scope.maximum(i)));
                    available = childConstraints(allocation[i], target, true);
                }
                sizes[i] = scope.measure(i, available);
            }
            extent += scope.linearSlot(i).weight() > 0 ? allocation[i] : main(sizes[i]);
            crossExtent = Math.max(crossExtent, cross(sizes[i]));
        }
        UiSize size = limits.constrain(new UiSize(horizontal ? extent : crossExtent, horizontal ? crossExtent : extent));
        List<Position> positions = new ArrayList<>(count);
        double cursor = 0;
        boolean previous = false;
        for (int i = 0; i < count; i++) {
            LinearSlot slot = scope.linearSlot(i);
            if (slot.crossSizing() == LinearSlot.CrossSizing.STRETCH) {
                double target = Math.min(cross(size), cross(scope.maximum(i)));
                double previousExtent = main(sizes[i]);
                double available = slot.weight() > 0 ? allocation[i] : previousExtent + Math.max(0, mainLimit - extent);
                // A final shared width can change wrapping or responsive columns. Give natural
                // content the remaining main-axis space and publish its remeasured extent.
                sizes[i] = scope.measure(i, childConstraints(available, target, true));
                if (slot.weight() == 0) extent += main(sizes[i]) - previousExtent;
            }
            double spare = cross(size) - cross(sizes[i]);
            double offset = switch (slot.alignment()) {
                case START -> 0;
                case CENTER -> spare / 2;
                case END -> spare;
            };
            if (spaced[i] && previous) cursor += gap;
            positions.add(new Position(horizontal ? cursor : offset, horizontal ? offset : cursor));
            cursor += slot.weight() > 0 ? allocation[i] : main(sizes[i]);
            previous |= spaced[i];
        }
        return new Result(limits.constrain(new UiSize(horizontal ? extent : crossExtent, horizontal ? crossExtent : extent)), positions);
    }
}
