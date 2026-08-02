package org.encinet.mik.module.menu;

import java.util.Arrays;

/** Player-selected size of Java Edition spatial menus. */
public enum FloatingMenuScale {
    SMALL("small", 0.82),
    NORMAL("normal", 1.00),
    LARGE("large", 1.18);

    private final String id;
    private final double factor;

    FloatingMenuScale(String id, double factor) {
        this.id = id;
        this.factor = factor;
    }

    public String id() {
        return id;
    }

    public double factor() {
        return factor;
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
