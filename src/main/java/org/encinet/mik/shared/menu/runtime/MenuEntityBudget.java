package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.ui.UiOverflowException;

/** One viewer's live menu entities and retained exit copies share this limit. */
final class MenuEntityBudget {
    static final int LIMIT = 2048;

    private MenuEntityBudget() { }

    static int remaining(int liveEntities) {
        return Math.max(0, LIMIT - liveEntities);
    }

    static void require(int entities, String owner) {
        require(entities,LIMIT,owner);
    }

    static void require(int entities, int capacity, String owner) {
        if (capacity < 0 || capacity > LIMIT) throw new IllegalArgumentException("Invalid menu entity capacity");
        if (entities > capacity) throw new UiOverflowException(owner + " exceeds its " + capacity + "-entity capacity");
    }
}
