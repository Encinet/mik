package org.encinet.mik.shared.menu.runtime.motion;

import org.encinet.mik.shared.menu.FloatingMenuEasing;

import java.util.Objects;

/** A finite, interruptible property transition sampled exclusively by its owner's monotonic tick clock. */
public final class MenuTransition {
    private final double minimum;
    private final double maximum;
    private double source;
    private double target;
    private long startedAt;
    private long sampledAt;
    private int duration;
    private FloatingMenuEasing easing = FloatingMenuEasing.CUBIC_OUT;

    public MenuTransition(double initial) {
        this(initial, 0, 1);
    }

    public MenuTransition(double initial, double minimum, double maximum) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum)
            throw new IllegalArgumentException("Invalid transition bounds");
        this.minimum = minimum;
        this.maximum = maximum;
        requireValue(initial);
        source = target = initial;
    }

    public double value(long tick) {
        observe(tick);
        if (duration == 0 || tick - startedAt >= duration) return target;
        double progress = easing.apply((double) (tick - startedAt) / duration);
        return Math.clamp(source + (target - source) * progress, minimum, maximum);
    }

    public void to(double next, long tick, int ticks, FloatingMenuEasing curve) {
        requireValue(next);
        if (ticks < 0) throw new IllegalArgumentException("Transition duration cannot be negative");
        Objects.requireNonNull(curve);
        double current = value(tick);
        if (ticks == 0 || current == next) { snap(next, tick); return; }
        if (target == next) return;
        source = current;
        target = next;
        startedAt = tick;
        duration = ticks;
        easing = curve;
    }

    public double step(double next, long tick, int ticks, FloatingMenuEasing curve) {
        to(next, tick, ticks, curve);
        return value(tick);
    }

    public void snap(double next, long tick) {
        requireValue(next);
        observe(tick);
        source = target = next;
        startedAt = tick;
        duration = 0;
    }

    private void observe(long tick) {
        if (tick < sampledAt) throw new IllegalArgumentException("Transition clock cannot move backwards");
        sampledAt = tick;
    }

    private void requireValue(double value) {
        if (!Double.isFinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException("Transition values must lie within their finite bounds");
    }
}
