package org.encinet.mik.shared.menu;

/** Controls whether a live scene may choose a new spatial anchor while reconciling. */
public enum FloatingMenuAnchorMode {
    /** Re-evaluates visibility and framing when the scene definition changes. */
    ADAPTIVE,

    /** Keeps the reading reference; front panels stay within the reading cone, with shared obstacle fitting. */
    FIXED_FOR_SESSION,

    FOLLOW_PLAYER_POSITION,

    /** Task-owned side tools follow slowly, holding still while the viewer turns toward them. */
    INERTIAL_TOOLBAR;

    public boolean followsPosition() { return this == FOLLOW_PLAYER_POSITION || this == INERTIAL_TOOLBAR; }
}
