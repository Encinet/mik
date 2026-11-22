package org.encinet.mik.feature.community.governance.membership;

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
        boolean eligible,
        Duration requiredPlaytime
) {
    public ActiveMemberEligibility {
        Objects.requireNonNull(requiredPlaytime, "requiredPlaytime");
    }
}
