package org.encinet.mik.module.music.rhythm.mode.falling;

import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.music.rhythm.RhythmInput;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Fixed four-lane layout and vertical approach geometry for falling mode. */
public final class RhythmFallingLayout {
    public static final double HIT_LINE_UP = -0.42;
    public static final double LANE_FORWARD = 0.30;
    private static final double NOTE_SPAWN_UP = 1.38;
    private static final double LANE_COLUMN_SPACING = 0.58;
    private static final Map<RhythmInput, FloatingMenuPoint> TARGETS =
            createTargets();

    private RhythmFallingLayout() {
    }

    public static FloatingMenuPoint target(RhythmInput input) {
        return TARGETS.get(Objects.requireNonNull(input, "input"));
    }

    public static FloatingMenuPoint point(
            FloatingMenuPoint target, double progress) {
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(progress)) {
            throw new IllegalArgumentException(
                    "falling-note progress must be finite");
        }
        double up = NOTE_SPAWN_UP
                + progress * (target.up() - NOTE_SPAWN_UP);
        return new FloatingMenuPoint(target.right(), up, target.forward());
    }

    public static FloatingMenuPoint offset(
            FloatingMenuPoint target, double right, double up, double forward) {
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(right) || !Double.isFinite(up)
                || !Double.isFinite(forward)) {
            throw new IllegalArgumentException(
                    "falling-layout offset must be finite");
        }
        return new FloatingMenuPoint(target.right() + right, target.up() + up,
                target.forward() + forward);
    }

    private static Map<RhythmInput, FloatingMenuPoint> createTargets() {
        EnumMap<RhythmInput, FloatingMenuPoint> positions =
                new EnumMap<>(RhythmInput.class);
        positions.put(RhythmInput.ONE, targetAt(-1.5));
        positions.put(RhythmInput.TWO, targetAt(-0.5));
        positions.put(RhythmInput.THREE, targetAt(0.5));
        positions.put(RhythmInput.FOUR, targetAt(1.5));
        return Map.copyOf(positions);
    }

    private static FloatingMenuPoint targetAt(double column) {
        return new FloatingMenuPoint(column * LANE_COLUMN_SPACING,
                HIT_LINE_UP, LANE_FORWARD);
    }
}
