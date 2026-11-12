package org.encinet.mik.feature.community.governance.membership.activity;

import java.time.Duration;
import java.util.Objects;

/** Exact active-member checks used when a nomination or voter roll is frozen. */
public record ActiveMemberEligibility(
        boolean currentMember,
        boolean memberAgeMet,
        boolean playtimeMet,
        boolean participationDaysMet,
        boolean recentDaysMet,
        boolean noActiveBan,
        Duration requiredPlaytime
) {
    public ActiveMemberEligibility {
        Objects.requireNonNull(requiredPlaytime, "requiredPlaytime");
    }

    public boolean eligible() {
        return currentMember && memberAgeMet && playtimeMet && participationDaysMet
                && recentDaysMet && noActiveBan;
    }
}
