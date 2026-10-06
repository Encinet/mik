package org.encinet.mik.module.menu;

import java.util.Arrays;

/** Player-selected layout size of Java Edition spatial menus. */
public enum FloatingMenuScale {
    MINIMUM("minimum", 0.70),
    EXTRA_SMALL("extra-small", 0.80),
    SMALL("small", 0.90),
    NORMAL("normal", 1.00),
    LARGE("large", 1.10),
    EXTRA_LARGE("extra-large", 1.20),
    MAXIMUM("maximum", 1.30);

    private final String id;
    private final double layoutFactor;

    FloatingMenuScale(String id, double layoutFactor) {
        this.id = id;
        this.layoutFactor = layoutFactor;
    }

    public String id() {
        return id;
    }

    public double factor() {
        return layoutFactor;
    }

    public int percent() {
        return (int) Math.round(layoutFactor * 100.0);
    }

    /** Moves through the discrete size choices without wrapping at either edge. */
    public FloatingMenuScale step(int direction) {
        int target = Math.clamp(ordinal() + Integer.signum(direction),
                0, values().length - 1);
        return values()[target];
    }

    public static FloatingMenuScale fromId(String id) {
        if (id == null) return NORMAL;
        return Arrays.stream(values())
                .filter(option -> option.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(NORMAL);
    }
}
