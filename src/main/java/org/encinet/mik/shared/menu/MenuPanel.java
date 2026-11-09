package org.encinet.mik.shared.menu;

/** Feature-owned content embedded in a host screen, with the same lifetime as its selection. */
@FunctionalInterface
public interface MenuPanel extends AutoCloseable {
    MenuScene render();

    /** Optional local tools placed before the host-owned close button when embedded as a popover. */
    default org.encinet.mik.shared.menu.ui.UiNode headerActions() { return null; }

    /** Called after the host is opened or this panel replaces its previous selection. */
    default void attach(FloatingMenuHandle handle) { }

    /** Releases input and pending presentation callbacks when the host leaves this panel. */
    @Override default void close() { }
}
