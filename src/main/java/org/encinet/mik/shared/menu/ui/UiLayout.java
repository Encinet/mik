package org.encinet.mik.shared.menu.ui;

import java.util.List;

/** Pure bounded measurement and local arrangement; implementations cannot access live game state. */
@FunctionalInterface
public interface UiLayout {
    Result layout(MeasureScope children, UiConstraints constraints);

    interface MeasureScope {
        int count();
        UiKey key(int index);
        UiSize measure(int index, UiConstraints constraints);
        UiSize minimum(int index);
        UiSize maximum(int index);
        UiSize measuredSize(int index);
        Position measuredFocus(int index);
        default java.util.Optional<UiBaseline> measuredBaseline(int index) { return java.util.Optional.empty(); }
        LinearSlot linearSlot(int index);
        GridSlot gridSlot(int index, int columns);
    }

    record Position(double x, double y) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Invalid UI position");
        }
    }

    record Result(UiSize size, List<Position> positions, java.util.Optional<Position> focus,
                  java.util.Optional<UiKey> focusTarget, java.util.Map<UiKey,UiSurfaceFacing> surfaceFacings) {
        public Result {
            java.util.Objects.requireNonNull(size, "size");
            positions = List.copyOf(positions);
            focus = java.util.Objects.requireNonNull(focus, "focus");
            focusTarget = java.util.Objects.requireNonNull(focusTarget, "focusTarget");
            surfaceFacings = java.util.Map.copyOf(surfaceFacings);
            if (focus.isPresent() && focusTarget.isPresent()) throw new IllegalArgumentException("Choose a focus position or a child");
        }
        public Result(UiSize size, List<Position> positions, java.util.Optional<Position> focus,
                      java.util.Optional<UiKey> focusTarget) {
            this(size,positions,focus,focusTarget,java.util.Map.of());
        }
        /** Layout owns direct surface orientation; other children and explicit FORWARD/INHERIT surfaces keep their authored plane. */
        public Result facing(UiKey child, UiSurfaceFacing facing) {
            var changed = new java.util.HashMap<>(surfaceFacings); changed.put(child,facing);
            return new Result(size,positions,focus,focusTarget,changed);
        }
        public Result(UiSize size, List<Position> positions, java.util.Optional<Position> focus) {
            this(size, positions, focus, java.util.Optional.empty());
        }
        public Result(UiSize size, List<Position> positions) { this(size, positions, java.util.Optional.empty()); }
        public Result focusing(UiKey child) {
            return new Result(size, positions, java.util.Optional.empty(), java.util.Optional.of(child),surfaceFacings);
        }
    }
}
