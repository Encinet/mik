package org.encinet.mik.shared.menu.ui;

/** Resolved pointer capability: reading help never implies an available operation. */
public enum UiPointerBehavior {
    NONE,
    INSPECT,
    OPERATE;

    public boolean tracksPointer() { return this != NONE; }

    public static UiPointerBehavior resolve(boolean enabled, boolean hasActions, boolean hasInspection) {
        if (enabled && hasActions) return OPERATE;
        return hasActions || hasInspection ? INSPECT : NONE;
    }
}
