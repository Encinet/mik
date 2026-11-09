package org.encinet.mik.shared.menu.runtime;

/** Expected placement failure; no display or input resources should be published for this frame. */
final class MenuSpaceUnavailableException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    MenuSpaceUnavailableException() {
        super("No unobstructed space for the menu in front of the viewer", null, false, false);
    }
}
