package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuElementState;
import org.encinet.mik.shared.menu.ui.UiPointerBehavior;

/** Pointer inspection and persistent choice state are independent of action feedback. */
final class MenuNodeFeedback {
    private MenuNodeFeedback() { }

    static double hover(UiPointerBehavior behavior, boolean pointed) {
        return behavior == UiPointerBehavior.OPERATE && pointed ? 1 : 0;
    }

    static FloatingMenuElementState state(UiPointerBehavior behavior, boolean enabled, boolean selected,
                                         boolean pointed, boolean pressed) {
        if (!enabled) return FloatingMenuElementState.DISABLED;
        if (behavior == UiPointerBehavior.OPERATE) {
            if (pressed) return FloatingMenuElementState.PRESSED;
            if (pointed) return FloatingMenuElementState.HOVERED;
        }
        return selected ? FloatingMenuElementState.SELECTED : FloatingMenuElementState.NORMAL;
    }
}
