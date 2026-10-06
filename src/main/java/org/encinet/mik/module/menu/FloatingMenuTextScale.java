package org.encinet.mik.module.menu;

import java.util.Arrays;

public enum FloatingMenuTextScale {
    MINIMUM(70),
    EXTRA_SMALL(80),
    SMALL(90),
    NORMAL(100),
    LARGE(110),
    EXTRA_LARGE(125),
    HUGE(140),
    EXTRA_HUGE(160),
    MAXIMUM(180);

    private final int percent;

    FloatingMenuTextScale(int percent) {
        this.percent = percent;
    }

    public String id() { return Integer.toString(percent); }

    public int percent() { return percent; }

    public double factor() { return percent / 100.0; }

    public FloatingMenuTextScale step(int direction) {
        return values()[Math.clamp(ordinal() + Integer.signum(direction), 0, values().length - 1)];
    }

    public static FloatingMenuTextScale fromId(String id) {
        return Arrays.stream(values()).filter(option -> option.id().equals(id)).findFirst().orElse(NORMAL);
    }
}
