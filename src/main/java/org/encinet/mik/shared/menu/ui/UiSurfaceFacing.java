package org.encinet.mik.shared.menu.ui;

/** Orientation belongs to a whole reading surface, never to its individual controls. */
public enum UiSurfaceFacing {
    /** Keep cards and backgrounds on their nearest owning plane. */
    INHERIT,
    /** Fold on one axis: side panels turn horizontally; upper and lower panels tilt vertically. */
    VIEWER,
    /** A sidebar turns horizontally even when it extends above or below the central page. */
    HORIZONTAL,
    /** A toolbar tilts vertically even when it is narrower or off-center. */
    VERTICAL,
    /** Stay parallel to the scene's primary reading plane. */
    FORWARD
}
