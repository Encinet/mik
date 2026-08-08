package org.encinet.mik.module.menu;

import java.util.Arrays;

/** Player-selected reading size of Java Edition spatial menus. */
public enum FloatingMenuScale {
    SMALL("small", 0.90, 0.75),
    NORMAL("normal", 1.00, 1.00),
    LARGE("large", 1.10, 1.40);

    private final String id;
    private final double layoutFactor;
    private final double textFactor;

    FloatingMenuScale(String id, double layoutFactor, double textFactor) {
        this.id = id;
        this.layoutFactor = layoutFactor;
        this.textFactor = textFactor;
    }

    public String id() {
        return id;
    }

    public double factor() {
        return layoutFactor;
    }

    /** Effective glyph size relative to the normal option in an open scene. */
    public double textFactor() {
        return textFactor;
    }

    /** Extra local glyph scale after the shared spatial layout scale is applied. */
    double typographyFactor() {
        return textFactor / layoutFactor;
    }

    public int textPercent() {
        return (int) Math.round(textFactor * 100.0);
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
