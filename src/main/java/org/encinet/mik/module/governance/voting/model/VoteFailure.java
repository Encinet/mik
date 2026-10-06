package org.encinet.mik.module.governance.voting.model;

public enum VoteFailure {
    NONE,
    QUORUM,
    MINIMUM_YES,
    TWO_THIRDS_NON_ABSTAINING,
    MAJORITY_OF_ALL_BALLOTS
}
