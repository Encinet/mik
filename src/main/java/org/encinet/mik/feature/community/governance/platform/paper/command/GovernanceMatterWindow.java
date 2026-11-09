package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.List;
import java.util.Objects;
import org.encinet.mik.shared.menu.FloatingMenuPage;

/** Storage batches stay bounded; navigation maps measured display pages back to source indices. */
record GovernanceMatterWindow(List<GovernanceMatter> items,int offset,int total,int anchor,boolean completed) {
    static final int HISTORY_BATCH_SIZE = 32;

    GovernanceMatterWindow {
        items = List.copyOf(items);
        if (offset < 0 || total < 0 || anchor < 0 || (long) offset + items.size() > total)
            throw new IllegalArgumentException("Invalid matter source window");
        if (completed && offset % HISTORY_BATCH_SIZE != 0) throw new IllegalArgumentException("History window is not a storage batch");
    }

    static GovernanceMatterWindow of(GovernanceWorkspaceState state,GovernanceWorkspaceData data) {
        if (state.filter == GovernanceWorkspaceState.Filter.COMPLETED)
            return new GovernanceMatterWindow(data.history().stream().map(GovernanceMatter::vote).toList(),
                    data.historyOffset(),data.historyTotal(),state.matterAnchor,true);
        var items = data.ongoing(state.subjectFilter);
        return new GovernanceMatterWindow(items,0,items.size(),state.matterAnchor,false);
    }

    int localAnchor() { return Math.clamp((long) anchor - offset,0,Math.max(0,items.size() - 1)); }

    GovernanceMatter selected(String id) {
        return items.stream().filter(matter -> Objects.equals(matter.id(),id)).findFirst()
                .orElse(items.isEmpty() ? null : items.get(localAnchor()));
    }

    FloatingMenuPage navigation(FloatingMenuPage local) {
        if (!completed) return local;
        int perBatch = divide(HISTORY_BATCH_SIZE,local.pageSize());
        int pages = total / HISTORY_BATCH_SIZE * perBatch + divide(total % HISTORY_BATCH_SIZE,local.pageSize());
        return new FloatingMenuPage(offset / HISTORY_BATCH_SIZE * perBatch + local.index(),pages,1);
    }

    int sourceIndex(int page,int capacity) {
        long index = completed ? (long) (page / divide(HISTORY_BATCH_SIZE,capacity)) * HISTORY_BATCH_SIZE
                + (long) (page % divide(HISTORY_BATCH_SIZE,capacity)) * capacity : (long) page * capacity;
        return Math.clamp(index,0,Math.max(0,total - 1));
    }

    private static int divide(int value,int divisor) { return (int) (((long) value + divisor - 1) / divisor); }
}
