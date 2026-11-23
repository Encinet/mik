package org.encinet.mik.shared.menu.runtime.space;

/** Expected placement failure; no display or input resources should be published for this frame. */
public final class MenuSpaceUnavailableException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public MenuSpaceUnavailableException() {
        super("No unobstructed space for the menu in front of the viewer", null, false, false);
    }
}
