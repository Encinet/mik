package org.encinet.mik.shared.menu;

/** Independent visual state of one node inside a menu session. */
public enum FloatingMenuElementState {
    NORMAL,
    /** Persistent semantic selection, independent of transient press/focus feedback. */
    SELECTED,
    HOVERED,
    PRESSED,
    DISABLED
}
