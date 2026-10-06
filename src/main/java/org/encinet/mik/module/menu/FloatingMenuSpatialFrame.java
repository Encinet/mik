package org.encinet.mik.module.menu;

/** Where local menu coordinates are anchored in the viewer's world. */
public enum FloatingMenuSpatialFrame {
    /** A readable scene placed ahead of the viewer. */
    IN_FRONT,

    FRONT_ARC,

    /** A scene whose local origin is the viewer, allowing a full surrounding ring. */
    AROUND_VIEWER;

    public boolean viewerCentered() {
        return this != IN_FRONT;
    }
}
