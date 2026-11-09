package org.encinet.mik.shared.menu;

/** Semantic wrapping widths for text surfaces without exposing renderer measurements to menus. */
public enum FloatingMenuTextWidth {
    /** Uses the component's available width for controls and a readable width for passive text. */
    AUTO(0),
    /** Compact labels on a circle around the viewer. */
    RING(92),
    /** Keeps short labels visually compact. */
    COMPACT(104),
    /** Gives titles and secondary metadata room to remain readable. */
    WIDE(168),
    /** Allows information-heavy surfaces to use a broad spatial panel. */
    EXPANDED(208),
    /** Uses the natural label width, bounded by the item's final parent constraints. */
    CONTENT(4096);

    private final int lineWidthPixels;

    FloatingMenuTextWidth(int lineWidthPixels) {
        this.lineWidthPixels = lineWidthPixels;
    }

    /** Applies the content port's role default when no inherited policy was selected. */
    public int resolve(int automaticWidth) {
        return this == AUTO ? automaticWidth : lineWidthPixels;
    }

    /** Applies the typed component default before layout clamps it to its final parent bounds. */
    public int resolve(boolean interactive) {
        return resolve(interactive ? CONTENT.lineWidthPixels : 160);
    }
}
