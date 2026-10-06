package org.encinet.mik.module.governance.delivery;

import org.encinet.mik.module.governance.voting.model.VoteProposal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A durable, idempotent side effect produced by a passed proposal. */
public record GovernanceAction(
        long id,
        String actionKey,
        GovernanceActionType type,
        VoteProposal proposal,
        long sourceVoteId,
        Instant createdAt,
        int attemptCount,
        Instant lastAttemptAt,
        String lastError
) {
    public GovernanceAction {
        if (id <= 0 || sourceVoteId <= 0) {
            throw new IllegalArgumentException("action ids must be positive");
        }
        if (actionKey == null || actionKey.isBlank()) {
            throw new IllegalArgumentException("actionKey must not be blank");
        }
        actionKey = actionKey.strip();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(proposal, "proposal");
        if (type.voteKind() != proposal.kind()) {
            throw new IllegalArgumentException("action type does not match proposal kind");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        if ((attemptCount == 0) != (lastAttemptAt == null)) {
            throw new IllegalArgumentException("attempt metadata is inconsistent");
        }
        if (lastAttemptAt != null && lastAttemptAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastAttemptAt must not precede creation");
        }
        if (attemptCount > 0 && (lastError == null || lastError.isBlank())) {
            throw new IllegalArgumentException("a pending failed attempt needs an error");
        }
    }

    public UUID playerId() {
        return proposal.subjectId();
    }

    public String playerName() {
        return proposal.subjectName();
    }
}
