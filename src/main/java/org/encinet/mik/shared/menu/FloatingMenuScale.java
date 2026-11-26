package org.encinet.mik.shared.menu;

import java.util.List;

/** Player-selected size, as a whole percentage of the default spatial menu size. */
public record FloatingMenuScale(int percent) {
    public static final int MIN_PERCENT = 40;
    public static final int MAX_PERCENT = 125;
    public static final FloatingMenuScale MINIMUM = new FloatingMenuScale(MIN_PERCENT);
    public static final FloatingMenuScale MAXIMUM = new FloatingMenuScale(MAX_PERCENT);
    public static final FloatingMenuScale COMPACT = new FloatingMenuScale(54);
    public static final FloatingMenuScale EXTRA_SMALL = new FloatingMenuScale(62);
    public static final FloatingMenuScale SMALL = new FloatingMenuScale(69);
    public static final FloatingMenuScale MEDIUM = new FloatingMenuScale(77);
    public static final FloatingMenuScale LARGE = new FloatingMenuScale(85);
    public static final FloatingMenuScale EXTRA_LARGE = new FloatingMenuScale(92);
    public static final FloatingMenuScale DEFAULT = new FloatingMenuScale(100);
    public static final List<FloatingMenuScale> PRESETS = List.of(
            COMPACT, EXTRA_SMALL, SMALL, MEDIUM, LARGE, EXTRA_LARGE, DEFAULT);

    public FloatingMenuScale {
        if (percent < MIN_PERCENT || percent > MAX_PERCENT)
            throw new IllegalArgumentException("Menu size out of range: " + percent);
    }

    public double factor() {
        // The saved presets predate rounding their labels to whole percentages.
        return switch (percent) {
            case 54 -> 0.70;
            case 62 -> 0.80;
            case 69 -> 0.90;
            case 77 -> 1.00;
            case 85 -> 1.10;
            case 92 -> 1.20;
            default -> percent / 100.0 * 1.30;
        };
    }

    public FloatingMenuScale step(int direction) {
        return new FloatingMenuScale(Math.clamp(percent + Integer.signum(direction) * 5, MIN_PERCENT, MAX_PERCENT));
    }
}
