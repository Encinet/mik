package org.encinet.mik.module.governance.voting.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GovernanceVote(
        long id,
        VoteProposal proposal,
        Instant opensAt,
        Instant closesAt,
        VoteStatus status,
        int eligibleVoters,
        int quorumRequired,
        VoteTally tally,
        Instant finalizedAt,
        VoteFailure failure,
        VoteTerminationReason terminationReason
) {
    public GovernanceVote {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(opensAt, "opensAt");
        Objects.requireNonNull(closesAt, "closesAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(tally, "tally");
        Objects.requireNonNull(failure, "failure");
        if (!closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("closesAt must be after opensAt");
        }
        if (eligibleVoters < 2 || quorumRequired < 2 || quorumRequired > eligibleVoters) {
            throw new IllegalArgumentException("invalid electorate snapshot");
        }
        if (quorumRequired != VotingPolicy.quorum(eligibleVoters)) {
            throw new IllegalArgumentException("quorum does not match electorate snapshot");
        }
        if (tally.participating() > eligibleVoters) {
            throw new IllegalArgumentException("tally exceeds frozen electorate");
        }
        if (finalizedAt != null && finalizedAt.isBefore(opensAt)) {
            throw new IllegalArgumentException("finalizedAt must not precede opening");
        }
        switch (status) {
            case OPEN -> {
                if (finalizedAt != null || failure != VoteFailure.NONE
                        || terminationReason != null) {
                    throw new IllegalArgumentException("open vote has final result metadata");
                }
            }
            case PASSED -> {
                if (finalizedAt == null || failure != VoteFailure.NONE
                        || terminationReason != null || finalizedAt.isBefore(closesAt)) {
                    throw new IllegalArgumentException("passed vote metadata is inconsistent");
                }
            }
            case FAILED -> {
                if (finalizedAt == null || failure == VoteFailure.NONE
                        || terminationReason != null || finalizedAt.isBefore(closesAt)) {
                    throw new IllegalArgumentException("failed vote metadata is inconsistent");
                }
            }
            case TERMINATED -> {
                if (finalizedAt == null || failure != VoteFailure.NONE
                        || terminationReason == null || !finalizedAt.isBefore(closesAt)) {
                    throw new IllegalArgumentException("terminated vote needs a reason");
                }
            }
        }
    }

    public boolean isOpenAt(Instant now) {
        return status == VoteStatus.OPEN && now.isBefore(closesAt);
    }

    public VoteKind kind() {
        return proposal.kind();
    }

    public UUID subjectId() {
        return proposal.subjectId();
    }

    public String subjectName() {
        return proposal.subjectName();
    }
}
