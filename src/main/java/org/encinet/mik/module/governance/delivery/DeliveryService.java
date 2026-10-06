package org.encinet.mik.module.governance.delivery;

import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Application boundary for the durable governance outbox. */
public final class DeliveryService {
    private final DeliveryRepository repository;

    public DeliveryService(DeliveryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<GovernanceAction> pendingActions() throws GovernanceException {
        try {
            return repository.pendingActions();
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load pending governance actions", error);
        }
    }

    public List<GovernanceVote> pendingVoteAnnouncements() throws GovernanceException {
        try {
            return repository.pendingVoteAnnouncements();
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load pending vote announcements", error);
        }
    }

    public void markVoteResultAnnounced(long voteId, Instant announcedAt)
            throws GovernanceException {
        Objects.requireNonNull(announcedAt, "announcedAt");
        try {
            repository.markVoteResultAnnounced(voteId, announcedAt);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not complete vote result announcement", error);
        }
    }

    public void markActionApplied(long actionId, Instant appliedAt)
            throws GovernanceException {
        Objects.requireNonNull(appliedAt, "appliedAt");
        try {
            repository.markActionApplied(actionId, appliedAt);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not complete governance action " + actionId, error);
        }
    }

    public void markActionFailed(long actionId, Instant attemptedAt, String error)
            throws GovernanceException {
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        try {
            repository.markActionFailed(actionId, attemptedAt, error);
        } catch (GovernanceRepositoryException | RuntimeException storageError) {
            throw storageFailure("Could not record failed governance action " + actionId,
                    storageError);
        }
    }

    private static GovernanceException storageFailure(String message, Throwable error) {
        return new GovernanceException(message, error);
    }
}
