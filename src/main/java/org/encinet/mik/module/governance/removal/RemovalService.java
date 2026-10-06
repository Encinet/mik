package org.encinet.mik.module.governance.removal;

import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.voting.VotingRepository;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VotingPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use cases for collecting sponsors and atomically opening removal votes. */
public final class RemovalService {
    private final RemovalRepository repository;
    private final VotingRepository votes;
    private final MembershipService membership;
    private final Clock clock;

    public RemovalService(
            RemovalRepository repository,
            VotingRepository votes,
            MembershipService membership
    ) {
        this(repository, votes, membership, Clock.systemUTC());
    }

    public RemovalService(
            RemovalRepository repository,
            VotingRepository votes,
            MembershipService membership,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.votes = Objects.requireNonNull(votes, "votes");
        this.membership = Objects.requireNonNull(membership, "membership");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RemovalSponsorship sponsorRemoval(
            PlayerProfile subject,
            UUID sponsorId
    ) throws GovernanceException {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(sponsorId, "sponsorId");
        if (subject.playerId().equals(sponsorId)) {
            throw new GovernanceException(GovernanceException.Code.REMOVAL_SELF);
        }
        List<PlayerProfile> electorate = membership.activeMembers();
        if (electorate.stream().noneMatch(player -> player.playerId().equals(sponsorId))) {
            throw new GovernanceException(GovernanceException.Code.REMOVAL_SPONSOR_INELIGIBLE);
        }
        int activeOthers = (int) electorate.stream()
                .filter(player -> !player.playerId().equals(subject.playerId()))
                .count();
        int required = RemovalPolicy.sponsorsRequired(activeOthers);
        if (required == Integer.MAX_VALUE) {
            throw new GovernanceException(GovernanceException.Code.REMOVAL_SPONSORS_TOO_FEW);
        }
        Instant now = clock.instant();
        try {
            if (votes.openVote(VoteKind.REMOVAL, subject.playerId()).isPresent()) {
                throw new GovernanceException(GovernanceException.Code.REMOVAL_VOTE_OPEN);
            }
            Optional<Instant> failedAt = votes.lastFailedAt(
                    VoteKind.REMOVAL, subject.playerId());
            if (failedAt.isPresent()
                    && failedAt.get().plus(VotingPolicy.FAILED_VOTE_COOLDOWN).isAfter(now)) {
                throw new GovernanceException(GovernanceException.Code.VOTE_COOLDOWN);
            }
            RemovalPetition petition = repository.gatheringRemovalPetition(
                            subject.playerId(), now)
                    .orElseGet(() -> createPetition(subject, required, now));
            Set<UUID> activeIds = electorate.stream()
                    .map(PlayerProfile::playerId)
                    .collect(Collectors.toUnmodifiableSet());
            for (UUID previousSponsor : repository.removalSponsors(petition.id())) {
                if (!activeIds.contains(previousSponsor)
                        || previousSponsor.equals(subject.playerId())) {
                    repository.removeRemovalSponsor(petition.id(), previousSponsor);
                }
            }
            return repository.sponsorRemoval(
                    petition.id(), subject.playerId(), subject.playerName(), sponsorId,
                    now, required, electorate);
        } catch (GovernanceException error) {
            throw error;
        } catch (RepositoryFailure error) {
            throw storageFailure("Could not create removal petition", error.getCause());
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not sponsor moderator removal", error);
        }
    }

    public List<RemovalPetition> gatheringPetitions() throws GovernanceException {
        try {
            return repository.gatheringRemovalPetitions(clock.instant());
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load removal petitions", error);
        }
    }

    private RemovalPetition createPetition(
            PlayerProfile subject,
            int sponsorsRequired,
            Instant now
    ) {
        try {
            return repository.createRemovalPetition(subject.playerId(),
                    subject.playerName(), now, sponsorsRequired);
        } catch (GovernanceRepositoryException error) {
            throw new RepositoryFailure(error);
        }
    }

    private static GovernanceException storageFailure(String message, Throwable error) {
        return new GovernanceException(message, error);
    }

    private static final class RepositoryFailure extends RuntimeException {
        private RepositoryFailure(Throwable cause) {
            super(cause);
        }
    }
}
