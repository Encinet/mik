package org.encinet.mik.shared.menu;

import java.util.List;

/** Typography size, independent of the spatial menu size. */
public record FloatingMenuTextScale(int percent) {
    public static final int MIN_PERCENT = 60;
    public static final int MAX_PERCENT = 200;
    public static final FloatingMenuTextScale MINIMUM = new FloatingMenuTextScale(MIN_PERCENT);
    public static final FloatingMenuTextScale MAXIMUM = new FloatingMenuTextScale(MAX_PERCENT);
    public static final FloatingMenuTextScale EXTRA_SMALL = new FloatingMenuTextScale(80);
    public static final FloatingMenuTextScale SMALL = new FloatingMenuTextScale(90);
    public static final FloatingMenuTextScale NORMAL = new FloatingMenuTextScale(100);
    public static final FloatingMenuTextScale LARGE = new FloatingMenuTextScale(110);
    public static final FloatingMenuTextScale EXTRA_LARGE = new FloatingMenuTextScale(125);
    public static final FloatingMenuTextScale HUGE = new FloatingMenuTextScale(140);
    public static final FloatingMenuTextScale EXTRA_HUGE = new FloatingMenuTextScale(160);
    public static final List<FloatingMenuTextScale> PRESETS = List.of(new FloatingMenuTextScale(70), EXTRA_SMALL,
            SMALL, NORMAL, LARGE, EXTRA_LARGE, HUGE, EXTRA_HUGE, new FloatingMenuTextScale(180));

    public FloatingMenuTextScale {
        if (percent < MIN_PERCENT || percent > MAX_PERCENT)
            throw new IllegalArgumentException("Text size out of range: " + percent);
    }
    public double factor() { return percent / 100.0; }

    public FloatingMenuTextScale step(int direction) {
        return new FloatingMenuTextScale(Math.clamp(percent + Integer.signum(direction) * 5, MIN_PERCENT, MAX_PERCENT));
    }
}
