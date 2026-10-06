package org.encinet.mik.module.governance.removal;

import org.encinet.mik.module.governance.voting.model.GovernanceVote;

public record RemovalSponsorship(
        RemovalPetition petition,
        GovernanceVote startedVote,
        boolean newlyAdded
) {
}
