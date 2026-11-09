package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.List;

/** Small immutable read model without staff or history; participation belongs only to its requester. */
record GovernanceActivityData(List<GovernanceMatter> matters, boolean activeMember) {
    GovernanceActivityData { matters = List.copyOf(matters); }
    GovernanceMatter find(String id) {
        return matters.stream().filter(matter -> matter.id().equals(id)).findFirst().orElse(null);
    }
}
