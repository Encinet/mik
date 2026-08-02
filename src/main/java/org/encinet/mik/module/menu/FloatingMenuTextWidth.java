package org.encinet.mik.module.menu;

/** Semantic wrapping widths for text surfaces without exposing renderer measurements to menus. */
public enum FloatingMenuTextWidth {
    /** Selects the width appropriate to the node's semantic role. */
    AUTO(0),
    /** Keeps short labels visually compact. */
    COMPACT(104),
    /** Gives titles and secondary metadata room to remain readable. */
    WIDE(168),
    /** Allows information-heavy surfaces to use a broad spatial panel. */
    EXPANDED(208);

    private final int lineWidthPixels;

    FloatingMenuTextWidth(int lineWidthPixels) {
        this.lineWidthPixels = lineWidthPixels;
    }

    int resolve(int automaticWidth) {
        return this == AUTO ? automaticWidth : lineWidthPixels;
    }
}
