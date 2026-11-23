package org.encinet.mik.shared.menu;

import org.encinet.mik.shared.menu.ui.UiNode;

/** An inline form draft owned by the host screen. Close it when that screen is suspended or closed. */
public interface MenuFormSession extends AutoCloseable {
    enum Actions { SUBMIT, SUBMIT_AND_CANCEL }
    default UiNode content() { return content(Actions.SUBMIT_AND_CANCEL); }
    /** A popover may own the equivalent cancellation through its header close button. */
    UiNode content(Actions actions);
    /** Reveals the field without acquiring input; clicking its preview explicitly starts editing. */
    void focus(String field);
    /** Explicit UI request to start the declared input method for a field. */
    void edit(String field);
    @Override void close();
}
