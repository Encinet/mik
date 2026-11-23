package org.encinet.mik.shared.menu.runtime.input;

import org.encinet.mik.shared.menu.MenuPopover;

/** Layer hit policy, shared by pointer picking and activation; dismissal never passes through to the world. */
public record MenuPopoverInput(String source, boolean modal) {
    public enum Activation { ACT, DISMISS, CONSUME }
    public enum ScrollScope { PARENT, POPOVER, BLOCKED }

    public boolean allows(String target, boolean covered) {
        return MenuPopover.owns(target) || !modal && !covered;
    }

    public Activation activation(String target, boolean inside, boolean scrolling) {
        if (scrolling) return switch (scrollScope(target,inside)) {
            case POPOVER -> Activation.ACT;
            case PARENT -> Activation.DISMISS;
            case BLOCKED -> Activation.CONSUME;
        };
        if (target == null) return inside ? Activation.CONSUME : Activation.DISMISS;
        if (MenuPopover.owns(target)) return Activation.ACT;
        if (modal) return Activation.CONSUME;
        return Activation.DISMISS;
    }

    public ScrollScope scrollScope(String target, boolean inside) {
        if (MenuPopover.owns(target) || inside) return ScrollScope.POPOVER;
        return modal ? ScrollScope.BLOCKED : ScrollScope.PARENT;
    }
}
