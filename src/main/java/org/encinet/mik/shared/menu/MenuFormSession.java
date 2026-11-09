package org.encinet.mik.shared.menu;

import org.encinet.mik.shared.menu.ui.UiNode;

/** An inline form draft owned by the host screen. Close it when that screen is suspended or closed. */
public interface MenuFormSession extends AutoCloseable {
    enum Actions { SUBMIT, SUBMIT_AND_CANCEL }
    default UiNode content() { return content(Actions.SUBMIT_AND_CANCEL); }
    /** A popover may own the equivalent cancellation through its header close button. */
    UiNode content(Actions actions);
    /** Opens a native editor with the declared field's complete draft without discarding other draft values. */
    void focus(String field);
    @Override void close();
}
