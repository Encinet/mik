package org.encinet.mik.module.governance.voting.model;

import java.time.Duration;
import java.util.Objects;

/** Deterministic rules shared by all governance proposal kinds. */
public final class VotingPolicy {
    public static final Duration VOTE_DURATION = Duration.ofDays(3);
    public static final Duration FAILED_VOTE_COOLDOWN = Duration.ofDays(3);

    private VotingPolicy() {
    }

    public static int quorum(int activeMembers) {
        if (activeMembers <= 1) {
            return Integer.MAX_VALUE;
        }
        if (activeMembers <= 3) {
            return 2;
        }
        return Math.max(3, ceilDiv(activeMembers, 4));
    }

    public static VoteResult evaluateVote(int eligibleVoters, VoteTally tally) {
        Objects.requireNonNull(tally, "tally");
        if (eligibleVoters < 2) {
            throw new IllegalArgumentException(
                    "a vote requires at least two eligible voters");
        }
        if (tally.participating() > eligibleVoters) {
            throw new IllegalArgumentException("tally exceeds the frozen electorate");
        }
        int quorum = quorum(eligibleVoters);
        if (tally.participating() < quorum) {
            return new VoteResult(false, VoteFailure.QUORUM, quorum);
        }
        int requiredMinimumYes = eligibleVoters >= 4 ? 3 : 2;
        if (tally.yes() < requiredMinimumYes) {
            return new VoteResult(false, VoteFailure.MINIMUM_YES, quorum);
        }
        int nonAbstaining = tally.yes() + tally.no();
        if (nonAbstaining == 0 || tally.yes() * 3L < nonAbstaining * 2L) {
            return new VoteResult(
                    false, VoteFailure.TWO_THIRDS_NON_ABSTAINING, quorum);
        }
        if (tally.yes() * 2L <= tally.participating()) {
            return new VoteResult(
                    false, VoteFailure.MAJORITY_OF_ALL_BALLOTS, quorum);
        }
        return new VoteResult(true, VoteFailure.NONE, quorum);
    }

    private static int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
