package org.encinet.mik.module.governance.voting.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VotingPolicyTest {

    @Test
    void quorumMatchesSmallAndLargeElectorates() {
        assertEquals(Integer.MAX_VALUE, VotingPolicy.quorum(1));
        assertEquals(2, VotingPolicy.quorum(2));
        assertEquals(2, VotingPolicy.quorum(3));
        assertEquals(3, VotingPolicy.quorum(4));
        assertEquals(3, VotingPolicy.quorum(12));
        assertEquals(4, VotingPolicy.quorum(13));
    }

    @Test
    void abstentionsCountForQuorumAndAbsoluteMajority() {
        VoteResult passed = VotingPolicy.evaluateVote(8, new VoteTally(3, 1, 1));
        VoteResult blockedByAllBallots = VotingPolicy.evaluateVote(
                8, new VoteTally(3, 1, 2));

        assertTrue(passed.passed());
        assertFalse(blockedByAllBallots.passed());
        assertEquals(VoteFailure.MAJORITY_OF_ALL_BALLOTS,
                blockedByAllBallots.failure());
    }

    @Test
    void twoThirdsIsInclusiveButAllBallotMajorityIsStrict() {
        assertTrue(VotingPolicy.evaluateVote(6, new VoteTally(4, 2, 0)).passed());
        assertFalse(VotingPolicy.evaluateVote(6, new VoteTally(3, 1, 2)).passed());
    }

    @Test
    void tallyCannotExceedFrozenElectorate() {
        assertThrows(IllegalArgumentException.class,
                () -> VotingPolicy.evaluateVote(2, new VoteTally(2, 1, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> VotingPolicy.evaluateVote(1, new VoteTally(1, 0, 0)));
    }

}
