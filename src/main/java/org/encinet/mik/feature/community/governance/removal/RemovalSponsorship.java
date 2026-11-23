package org.encinet.mik.feature.community.governance.removal;

import org.encinet.mik.feature.community.governance.voting.GovernanceVote;

public record RemovalSponsorship(
        RemovalPetition petition,
        GovernanceVote startedVote,
        boolean newlyAdded
) {
}
