package org.encinet.mik.module.governance.delivery;

import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;

import java.time.Instant;
import java.util.List;

/** Durable result-announcement and side-effect outbox. */
public interface DeliveryRepository {
    List<GovernanceAction> pendingActions() throws GovernanceRepositoryException;

    void markActionApplied(long actionId, Instant appliedAt)
            throws GovernanceRepositoryException;

    void markActionFailed(long actionId, Instant attemptedAt, String error)
            throws GovernanceRepositoryException;

    List<GovernanceVote> pendingVoteAnnouncements() throws GovernanceRepositoryException;

    void markVoteResultAnnounced(long voteId, Instant announcedAt)
            throws GovernanceRepositoryException;
}
