package org.encinet.mik.shared.menu;

/** A view-owned input session. Completion and cancellation run at most once on the server thread. */
public interface MenuInputLease extends AutoCloseable {
    boolean active();
    boolean complete();
    @Override void close();
}
