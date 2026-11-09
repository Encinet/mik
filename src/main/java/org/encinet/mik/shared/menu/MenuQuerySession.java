package org.encinet.mik.shared.menu;

import org.encinet.mik.shared.menu.ui.UiNode;

/**
 * A single-line query backed by the host's state, submitted by one chat message.
 * Submission releases chat capture before applying the query; Cancel only abandons an unsubmitted input.
 * Create once per visible field and close when its screen is suspended, replaced or closed.
 */
public interface MenuQuerySession extends AutoCloseable {
    /** Renders the current source value. Placeholder and editing state never enter query data. */
    UiNode content(String value);
    @Override void close();
}
