package org.encinet.mik.feature.community.governance.voting;

import java.util.List;
import org.encinet.mik.feature.community.governance.voting.model.GovernanceVote;

/** A bounded page of completed matters, optionally restricted to one subject. */
public record VoteHistory(List<GovernanceVote> votes, int total, int offset) {
    public VoteHistory { votes = List.copyOf(votes); }
}
