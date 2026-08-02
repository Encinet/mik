package org.encinet.mik.module.menu;

/** Lifecycle shared by every client-side floating menu. */
public enum FloatingMenuState {
    OPENING,
    ACTIVE,
    /** Screen is preserved below a child screen and can still receive state updates. */
    SUSPENDED,
    CLOSING,
    CLOSED
}
