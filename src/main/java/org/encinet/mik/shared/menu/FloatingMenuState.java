package org.encinet.mik.shared.menu;

/** Lifecycle shared by every client-side floating menu. */
public enum FloatingMenuState {
    OPENING,
    ACTIVE,
    /** Content is preserved below a child screen or while its world host is paused; updates remain allowed. */
    SUSPENDED,
    CLOSING,
    CLOSED
}
