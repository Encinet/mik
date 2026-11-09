package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.time.Instant;
import java.util.*;

/** Keeps displayed identities stable while a submission or attached companion owns them. */
final class GovernanceActivityState {
    private GovernanceActivityData data;
    private List<GovernanceMatter> displayed = List.of();
    private boolean list;
    private int holds;

    void update(GovernanceActivityData next) {
        data = Objects.requireNonNull(next);
        if (holds > 0) {
            displayed = displayed.stream().map(old -> Optional.ofNullable(next.find(old.id())).orElse(old)).toList();
            return;
        }
        var ordered = new ArrayList<GovernanceMatter>();
        for (var old : displayed) {
            var current = next.find(old.id());
            if (current != null) ordered.add(current);
        }
        for (var matter : next.matters()) if (ordered.stream().noneMatch(old -> old.id().equals(matter.id()))) ordered.add(matter);
        displayed = List.copyOf(ordered);
        // A list becoming shorter must not introduce vote buttons under an existing pointer.
        list = !displayed.isEmpty() && (list || displayed.size() > 1);
    }

    GovernanceActivityData data() { return data; }
    List<GovernanceMatter> displayed() { return displayed; }
    boolean single() { return !list && displayed.size() == 1; }
    void hold() { holds++; }
    void release() {
        if (holds <= 0) throw new IllegalStateException("No held governance presentation");
        if (--holds == 0 && data != null) update(data);
    }
    boolean current(GovernanceMatter matter, Instant now) {
        return data != null && data.find(matter.id()) != null && now.isBefore(matter.deadline());
    }
}
