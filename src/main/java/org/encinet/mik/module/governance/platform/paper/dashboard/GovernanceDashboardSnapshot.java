package org.encinet.mik.module.governance.platform.paper.dashboard;

import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;

import java.util.List;

/** Immutable data needed by the global main menu's governance section. */
public record GovernanceDashboardSnapshot(
        List<OpenVote> openVotes,
        List<GovernanceVote> recentResults
) {
    public GovernanceDashboardSnapshot {
        openVotes = List.copyOf(openVotes);
        recentResults = List.copyOf(recentResults);
    }

    public record OpenVote(GovernanceVote vote, boolean voter, VoteChoice ownChoice) { }
}
