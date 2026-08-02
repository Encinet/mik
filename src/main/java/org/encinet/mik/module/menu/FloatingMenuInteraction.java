package org.encinet.mik.module.menu;

/** Semantic input independent from the packet or client control that produced it. */
public enum FloatingMenuInteraction {
    PRIMARY,
    SECONDARY,
    /** Contextual shortcut activated by the client's swap-hands control (F by default). */
    HOTKEY,
    SCROLL_UP,
    SCROLL_DOWN;

    public boolean secondary() {
        return this == SECONDARY;
    }

    public boolean scroll() { return this == SCROLL_UP || this == SCROLL_DOWN; }
}
