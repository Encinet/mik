package org.encinet.mik.shared.menu;

/** One revocable close decision. A feature may show its own confirmation before resolving it. */
public interface MenuCloseRequest {
    FloatingMenuCloseReason reason();
    boolean active();
    void approve();
    void cancel();
}
