package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.MenuPopover;

/** Layer hit policy, shared by pointer picking and activation; dismissal never passes through to the world. */
record MenuPopoverInput(String source, boolean modal) {
    enum Activation { ACT, DISMISS, DISMISS_AND_ACT, CONSUME }
    enum ScrollScope { PARENT, POPOVER, BLOCKED }

    boolean allows(String target, boolean covered) {
        return MenuPopover.owns(target) || !modal && !covered;
    }

    Activation activation(String target, boolean inside, boolean scrolling) {
        if (scrolling) return switch (scrollScope(target,inside)) {
            case POPOVER -> Activation.ACT;
            case PARENT -> Activation.DISMISS_AND_ACT;
            case BLOCKED -> Activation.CONSUME;
        };
        if (target == null) return inside ? Activation.CONSUME : Activation.DISMISS;
        if (MenuPopover.owns(target)) return Activation.ACT;
        if (modal) return Activation.CONSUME;
        return source.equals(target) ? Activation.DISMISS : Activation.DISMISS_AND_ACT;
    }

    ScrollScope scrollScope(String target, boolean inside) {
        if (MenuPopover.owns(target) || inside) return ScrollScope.POPOVER;
        return modal ? ScrollScope.BLOCKED : ScrollScope.PARENT;
    }
}
