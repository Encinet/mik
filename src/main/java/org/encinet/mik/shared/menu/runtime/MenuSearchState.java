package org.encinet.mik.shared.menu.runtime;

import java.util.List;

/** Request versions prevent stale results from replacing a newer query or a closed screen. */
final class MenuSearchState<T> {
    static final int MAX_RESULTS = 256;
    String query = "";
    int page, total;
    List<T> results = List.of();
    boolean pending = true, failed;
    private boolean initialized;
    private long version;
    private boolean closed;

    boolean initializing() { return !initialized; }

    long begin(String value) {
        if (closed) throw new IllegalStateException("Search is closed");
        query = value.strip(); page = 0; total = 0; results = List.of(); pending = true; failed = false;
        return ++version;
    }

    boolean complete(long request, List<T> found, Throwable error) {
        if (closed || request != version) return false;
        initialized = true;
        total = error == null ? found.size() : 0;
        results = error == null ? List.copyOf(found.subList(0,Math.min(MAX_RESULTS,found.size()))) : List.of();
        pending = false; failed = error != null;
        return true;
    }

    void close() { closed = true; version++; results = List.of(); }
}
