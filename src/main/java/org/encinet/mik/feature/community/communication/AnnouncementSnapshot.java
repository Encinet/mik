package org.encinet.mik.feature.community.communication;

import java.util.List;

/** A known empty catalog is distinct from a catalog that has not loaded. */
public record AnnouncementSnapshot(State state, List<Announcement> entries) {
    static final AnnouncementSnapshot UNAVAILABLE = new AnnouncementSnapshot(State.UNAVAILABLE, List.of());

    public enum State { UNAVAILABLE, READY, STALE }

    public AnnouncementSnapshot {
        entries = List.copyOf(entries);
    }

    AnnouncementSnapshot retainAfterFailure() {
        return state == State.READY ? new AnnouncementSnapshot(State.STALE, entries) : this;
    }
}
