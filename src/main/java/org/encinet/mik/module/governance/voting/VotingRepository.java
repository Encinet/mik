package org.encinet.mik.module.governance.voting;

import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations owned by the voting capability. */
public interface VotingRepository {
    GovernanceVote createVote(
            VoteProposal proposal,
            Instant opensAt,
            Collection<PlayerProfile> voters
    ) throws GovernanceRepositoryException;

    boolean isVoter(long voteId, UUID voterId) throws GovernanceRepositoryException;

    void castBallot(long voteId, UUID voterId, VoteChoice choice, Instant castAt)
            throws GovernanceRepositoryException;

    Optional<VoteChoice> ballotChoice(long voteId, UUID voterId)
            throws GovernanceRepositoryException;

    Optional<GovernanceVote> vote(long voteId) throws GovernanceRepositoryException;

    List<GovernanceVote> openVotes() throws GovernanceRepositoryException;

    List<GovernanceVote> recentVotes(int limit) throws GovernanceRepositoryException;

    Optional<GovernanceVote> openVote(VoteKind kind, UUID subjectId)
            throws GovernanceRepositoryException;

    Optional<Instant> lastFailedAt(VoteKind kind, UUID subjectId)
            throws GovernanceRepositoryException;

    GovernanceVote finalizeVote(
            long voteId,
            VoteStatus status,
            VoteFailure failure,
            VoteTerminationReason terminationReason,
            Instant finalizedAt
    ) throws GovernanceRepositoryException;

}
