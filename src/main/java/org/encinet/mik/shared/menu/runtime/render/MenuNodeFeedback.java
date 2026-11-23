package org.encinet.mik.shared.menu.runtime.render;

import org.encinet.mik.shared.menu.FloatingMenuElementState;
import org.encinet.mik.shared.menu.ui.UiPointerBehavior;

/** Pointer inspection and persistent choice state are independent of action feedback. */
public final class MenuNodeFeedback {
    private MenuNodeFeedback() { }

    public static double hover(UiPointerBehavior behavior, boolean pointed) {
        return behavior == UiPointerBehavior.OPERATE && pointed ? 1 : 0;
    }

    public static FloatingMenuElementState state(UiPointerBehavior behavior, boolean enabled, boolean selected,
                                         boolean pointed, boolean pressed) {
        if (!enabled) return FloatingMenuElementState.DISABLED;
        if (behavior == UiPointerBehavior.OPERATE) {
            if (pressed) return FloatingMenuElementState.PRESSED;
            if (pointed) return FloatingMenuElementState.HOVERED;
        }
        return selected ? FloatingMenuElementState.SELECTED : FloatingMenuElementState.NORMAL;
    }
}
