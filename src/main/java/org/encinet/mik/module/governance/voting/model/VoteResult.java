package org.encinet.mik.module.governance.voting.model;

import java.util.Objects;

public record VoteResult(boolean passed, VoteFailure failure, int quorumRequired) {
    public VoteResult {
        Objects.requireNonNull(failure, "failure");
        if (quorumRequired < 0) {
            throw new IllegalArgumentException("quorumRequired must not be negative");
        }
        if (passed && failure != VoteFailure.NONE) {
            throw new IllegalArgumentException("a passed vote cannot have a failure");
        }
    }
}
