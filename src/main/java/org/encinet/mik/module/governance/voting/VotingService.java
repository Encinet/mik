package org.encinet.mik.module.governance.voting;

import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteResult;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.module.governance.voting.model.VotingPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Use cases for proposals, frozen electorates, ballots, and vote finalization. */
public final class VotingService {
    private final VotingRepository repository;
    private final MembershipService membership;
    private final Clock clock;

    public VotingService(VotingRepository repository, MembershipService membership) {
        this(repository, membership, Clock.systemUTC());
    }

    public VotingService(
            VotingRepository repository,
            MembershipService membership,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.membership = Objects.requireNonNull(membership, "membership");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Starts a proposal that does not require a separate co-sponsor workflow. */
    public GovernanceVote startVote(VoteProposal proposal) throws GovernanceException {
        Objects.requireNonNull(proposal, "proposal");
        List<PlayerProfile> electorate = membership.activeMembers();
        if (electorate.size() <= 1) {
            throw new GovernanceException(GovernanceException.Code.VOTERS_TOO_FEW);
        }
        try {
            PlayerProfile subject = membership.player(proposal.subjectId())
                    .orElseThrow(() -> new GovernanceException(GovernanceException.Code.VOTE_SUBJECT_UNKNOWN));
            VoteProposal canonical = canonicalProposal(proposal, subject, electorate);
            if (repository.openVote(canonical.kind(), canonical.subjectId()).isPresent()) {
                throw new GovernanceException(GovernanceException.Code.VOTE_ALREADY_OPEN);
            }
            Optional<Instant> failedAt = repository.lastFailedAt(
                    canonical.kind(), canonical.subjectId());
            if (failedAt.isPresent()
                    && failedAt.get().plus(VotingPolicy.FAILED_VOTE_COOLDOWN)
                    .isAfter(clock.instant())) {
                throw new GovernanceException(GovernanceException.Code.VOTE_COOLDOWN);
            }
            return repository.createVote(canonical, clock.instant(), electorate);
        } catch (GovernanceException error) {
            throw error;
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not start governance vote", error);
        }
    }

    public GovernanceVote castBallot(long voteId, UUID voterId, VoteChoice choice)
            throws GovernanceException {
        Objects.requireNonNull(voterId, "voterId");
        Objects.requireNonNull(choice, "choice");
        Instant now = clock.instant();
        try {
            GovernanceVote vote = repository.vote(voteId)
                    .orElseThrow(() -> new GovernanceException(GovernanceException.Code.VOTE_UNKNOWN));
            if (!vote.isOpenAt(now)) {
                throw new GovernanceException(GovernanceException.Code.VOTE_CLOSED);
            }
            if (!repository.isVoter(voteId, voterId)) {
                throw new GovernanceException(GovernanceException.Code.NOT_VOTER);
            }
            repository.castBallot(voteId, voterId, choice, now);
            return repository.vote(voteId).orElseThrow();
        } catch (GovernanceException error) {
            throw error;
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not cast ballot", error);
        }
    }

    public GovernanceVote terminateVote(long voteId, VoteTerminationReason reason)
            throws GovernanceException {
        Objects.requireNonNull(reason, "reason");
        try {
            GovernanceVote vote = repository.vote(voteId)
                    .orElseThrow(() -> new GovernanceException(GovernanceException.Code.VOTE_UNKNOWN));
            validateTerminationReason(vote.kind(), reason);
            Instant now = clock.instant();
            if (!vote.isOpenAt(now)) {
                throw new GovernanceException(GovernanceException.Code.VOTE_TERMINATION_CLOSED);
            }
            return repository.finalizeVote(voteId, VoteStatus.TERMINATED,
                    VoteFailure.NONE, reason, now);
        } catch (GovernanceException error) {
            throw error;
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not terminate vote", error);
        }
    }

    public Optional<GovernanceVote> terminateOpenVote(
            VoteKind kind,
            UUID subjectId,
            VoteTerminationReason reason
    ) throws GovernanceException {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(reason, "reason");
        validateTerminationReason(kind, reason);
        try {
            Optional<GovernanceVote> open = repository.openVote(kind, subjectId);
            if (open.isEmpty()) return Optional.empty();
            Instant now = clock.instant();
            if (!open.get().isOpenAt(now)) return Optional.empty();
            return Optional.of(repository.finalizeVote(open.get().id(), VoteStatus.TERMINATED,
                    VoteFailure.NONE, reason, now));
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not terminate open vote", error);
        }
    }

    public List<GovernanceVote> openVotes() throws GovernanceException {
        try {
            return repository.openVotes();
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load open votes", error);
        }
    }

    public GovernanceVote vote(long voteId) throws GovernanceException {
        try {
            return repository.vote(voteId)
                    .orElseThrow(() -> new GovernanceException(GovernanceException.Code.VOTE_UNKNOWN));
        } catch (GovernanceException error) {
            throw error;
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load vote", error);
        }
    }

    public boolean isVoter(long voteId, UUID voterId) throws GovernanceException {
        try {
            return repository.isVoter(voteId, voterId);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not inspect frozen voter roll", error);
        }
    }

    public Optional<VoteChoice> ballotChoice(long voteId, UUID voterId)
            throws GovernanceException {
        try {
            return repository.ballotChoice(voteId, voterId);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load own ballot", error);
        }
    }

    public List<GovernanceVote> recentVotes(int limit) throws GovernanceException {
        try {
            return repository.recentVotes(limit);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load vote history", error);
        }
    }

    public List<GovernanceVote> finalizeDueVotes() throws GovernanceException {
        Instant now = clock.instant();
        try {
            List<GovernanceVote> finalized = new ArrayList<>();
            for (GovernanceVote vote : repository.openVotes()) {
                if (vote.closesAt().isAfter(now)) continue;
                VoteResult result = VotingPolicy.evaluateVote(
                        vote.eligibleVoters(), vote.tally());
                finalized.add(repository.finalizeVote(vote.id(),
                        result.passed() ? VoteStatus.PASSED : VoteStatus.FAILED,
                        result.failure(), null, now));
            }
            return List.copyOf(finalized);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not finalize votes", error);
        }
    }

    private VoteProposal canonicalProposal(
            VoteProposal proposal,
            PlayerProfile subject,
            List<PlayerProfile> electorate
    ) throws GovernanceException {
        return switch (proposal) {
            case VoteProposal.Appointment appointment -> {
                if (electorate.stream().noneMatch(player -> player.playerId()
                        .equals(appointment.subjectId()))) {
                    throw new GovernanceException(GovernanceException.Code.CANDIDATE_INELIGIBLE);
                }
                yield new VoteProposal.Appointment(
                        subject.playerId(), subject.playerName());
            }
            case VoteProposal.Removal ignored -> throw new GovernanceException(
                    GovernanceException.Code.REMOVAL_REQUIRES_PETITION);
            case VoteProposal.Ban ban -> {
                if (ban.proposerId().equals(ban.subjectId())) {
                    throw new GovernanceException(GovernanceException.Code.BAN_SELF);
                }
                PlayerProfile proposer = electorate.stream()
                        .filter(player -> player.playerId().equals(ban.proposerId()))
                        .findFirst()
                        .orElseThrow(() -> new GovernanceException(
                                GovernanceException.Code.BAN_PROPOSER_INELIGIBLE));
                if (membership.isActivelyBanned(subject)) {
                    throw new GovernanceException(GovernanceException.Code.BAN_ALREADY_ACTIVE);
                }
                yield new VoteProposal.Ban(
                        subject.playerId(), subject.playerName(),
                        proposer.playerId(), proposer.playerName(),
                        ban.duration(), ban.reason());
            }
        };
    }

    private static void validateTerminationReason(
            VoteKind kind,
            VoteTerminationReason reason
    ) throws GovernanceException {
        boolean valid = switch (kind) {
            case APPOINTMENT -> reason == VoteTerminationReason.CANDIDATE_WITHDREW
                    || reason == VoteTerminationReason.CANDIDATE_INELIGIBLE;
            case REMOVAL -> reason == VoteTerminationReason.MODERATOR_RESIGNED
                    || reason == VoteTerminationReason.MODERATOR_AUTO_REMOVED;
            case BAN -> false;
        };
        if (!valid) {
            throw new GovernanceException(GovernanceException.Code.TERMINATION_REASON_INVALID);
        }
    }

    private static GovernanceException storageFailure(String message, Throwable error) {
        return new GovernanceException(message, error);
    }

}
