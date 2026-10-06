package org.encinet.mik.module.menu;

/** Controls whether a live scene may choose a new spatial anchor while reconciling. */
public enum FloatingMenuAnchorMode {
    /** Re-evaluates visibility and framing when the scene definition changes. */
    ADAPTIVE,

    /** Keeps the opening anchor stable while animated content moves inside the scene. */
    FIXED_FOR_SESSION,

    FOLLOW_PLAYER_POSITION
}
