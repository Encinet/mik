package org.encinet.mik.module.menu;

/** Selects which client presentation is allowed for a declarative scene. */
public enum FloatingMenuPresentation {
    /** Uses a native Bedrock form when available and virtual entities otherwise. */
    ADAPTIVE(true),

    /** Requires the spatial scene because client movement or depth is part of the interaction. */
    SPATIAL_REQUIRED(false);

    private final boolean nativeFormCompatible;

    FloatingMenuPresentation(boolean nativeFormCompatible) {
        this.nativeFormCompatible = nativeFormCompatible;
    }

    public boolean nativeFormCompatible() {
        return nativeFormCompatible;
    }
}
