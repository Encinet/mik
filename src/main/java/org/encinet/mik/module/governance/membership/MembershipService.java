package org.encinet.mik.module.governance.membership;

import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.module.governance.membership.activity.ActiveMembershipPolicy;
import org.encinet.mik.module.governance.membership.participation.ParticipationPolicy;
import org.encinet.mik.module.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.membership.promotion.PromotionEligibility;
import org.encinet.mik.module.governance.membership.promotion.PromotionPause;
import org.encinet.mik.module.governance.membership.promotion.PromotionPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Use cases for player history, participation, promotion, and electorate eligibility. */
public final class MembershipService {
    public static final ZoneId GOVERNANCE_ZONE = ZoneId.of("Asia/Singapore");

    private final MembershipRepository repository;
    private final Clock clock;
    private final ActiveBanLookup activeBans;
    private final CurrentMemberLookup currentMembers;

    public MembershipService(
            MembershipRepository repository,
            ActiveBanLookup activeBans,
            CurrentMemberLookup currentMembers
    ) {
        this(repository, Clock.systemUTC(), activeBans, currentMembers);
    }

    public MembershipService(
            MembershipRepository repository,
            Clock clock,
            ActiveBanLookup activeBans,
            CurrentMemberLookup currentMembers
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.activeBans = Objects.requireNonNull(activeBans, "activeBans");
        this.currentMembers = Objects.requireNonNull(currentMembers, "currentMembers");
    }

    /** Records a successful login, which is also the moderator inactivity source. */
    public void observeLogin(
            UUID playerId,
            String playerName,
            Instant firstJoinedAt,
            boolean alreadyMember
    ) throws GovernanceException {
        observeKnownPlayer(playerId, playerName, firstJoinedAt,
                clock.instant(), alreadyMember);
    }

    /** Imports server history without treating the inspection as a login. */
    public void observeKnownPlayer(
            UUID playerId,
            String playerName,
            Instant firstJoinedAt,
            Instant lastSuccessfulLoginAt,
            boolean alreadyMember
    ) throws GovernanceException {
        try {
            repository.observePlayer(playerId, playerName, firstJoinedAt,
                    lastSuccessfulLoginAt);
            if (alreadyMember) {
                repository.recordMemberSince(playerId, firstJoinedAt);
            }
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not record player history for " + playerName, error);
        }
    }

    public void recordPromotion(UUID playerId, Instant promotedAt) throws GovernanceException {
        try {
            repository.recordMemberSince(playerId, promotedAt);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not record member acquisition", error);
        }
    }

    /** Adds eligible online time, splitting it at UTC+8 natural-day boundaries. */
    public void recordEligibleInterval(UUID playerId, Instant from, Instant to)
            throws GovernanceException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!to.isAfter(from)) return;
        try {
            Instant cursor = from;
            while (cursor.isBefore(to)) {
                ZonedDateTime local = cursor.atZone(GOVERNANCE_ZONE);
                Instant nextMidnight = local.toLocalDate().plusDays(1)
                        .atStartOfDay(GOVERNANCE_ZONE).toInstant();
                Instant segmentEnd = nextMidnight.isBefore(to) ? nextMidnight : to;
                long seconds = Duration.between(cursor, segmentEnd).toSeconds();
                if (seconds > 0) {
                    repository.addParticipation(playerId, local.toLocalDate(), seconds);
                    cursor = cursor.plusSeconds(seconds);
                } else {
                    cursor = segmentEnd;
                }
            }
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not record effective playtime", error);
        }
    }

    public Optional<PlayerProfile> player(UUID playerId) throws GovernanceException {
        try {
            return repository.player(playerId);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load governance player", error);
        }
    }

    public ParticipationSummary participation(UUID playerId) throws GovernanceException {
        try {
            return ParticipationPolicy.summarize(repository.participation(playerId), today());
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not load effective playtime", error);
        }
    }

    public PromotionEligibility promotionEligibility(UUID playerId) throws GovernanceException {
        Instant now = clock.instant();
        try {
            PlayerProfile player = repository.player(playerId)
                    .orElseThrow(() -> new GovernanceException(GovernanceException.Code.PLAYER_UNKNOWN));
            ParticipationSummary participation = ParticipationPolicy.summarize(
                    repository.participation(playerId), today());
            PromotionPause pause = repository.activePause(playerId, now).orElse(null);
            return PromotionPolicy.evaluate(
                    player.firstJoinedAt(), participation.lifetimeSeconds(),
                    participation.lifetimePlayDays(),
                    activeBans.isActivelyBanned(player.playerId(), player.playerName()),
                    pause, now);
        } catch (GovernanceException error) {
            throw error;
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not evaluate promotion eligibility", error);
        }
    }

    public ActiveMemberEligibility activeMemberEligibility(UUID playerId)
            throws GovernanceException {
        Instant now = clock.instant();
        try {
            PlayerProfile player = repository.player(playerId)
                    .orElseThrow(() -> new GovernanceException(GovernanceException.Code.PLAYER_UNKNOWN));
            ParticipationSummary participation = ParticipationPolicy.summarize(
                    repository.participation(playerId), today());
            return ActiveMembershipPolicy.evaluate(
                    currentMembers.isCurrentlyMember(player.playerId(), player.playerName()),
                    player.memberSince(), participation,
                    activeBans.isActivelyBanned(player.playerId(), player.playerName()), now);
        } catch (GovernanceException error) {
            throw error;
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not evaluate active-member eligibility", error);
        }
    }

    public List<PlayerProfile> activeMembers() throws GovernanceException {
        Instant now = clock.instant();
        LocalDate today = today();
        try {
            List<PlayerProfile> active = new ArrayList<>();
            for (PlayerProfile player : repository.players()) {
                if (!player.isMember()) continue;
                if (!currentMembers.isCurrentlyMember(
                        player.playerId(), player.playerName())) continue;
                ParticipationSummary participation = ParticipationPolicy.summarize(
                        repository.participation(player.playerId()), today);
                if (ActiveMembershipPolicy.evaluate(
                        true, player.memberSince(), participation,
                        activeBans.isActivelyBanned(player.playerId(), player.playerName()), now)
                        .eligible()) {
                    active.add(player);
                }
            }
            active.sort(Comparator.comparing(PlayerProfile::playerName,
                    String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(active);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not build active-member voter roll", error);
        }
    }

    public boolean isActivelyBanned(PlayerProfile player) throws GovernanceException {
        Objects.requireNonNull(player, "player");
        try {
            return activeBans.isActivelyBanned(player.playerId(), player.playerName());
        } catch (RuntimeException error) {
            throw new GovernanceException("Could not inspect active ban", error);
        }
    }

    public PromotionPause pausePromotion(
            UUID playerId,
            String reason,
            Instant endsAt,
            String appealPath,
            String imposedBy
    ) throws GovernanceException {
        Instant now = clock.instant();
        if (!endsAt.isAfter(now)) {
            throw new GovernanceException(GovernanceException.Code.PAUSE_END_INVALID);
        }
        try {
            PlayerProfile player = repository.player(playerId)
                    .orElseThrow(() -> new GovernanceException(GovernanceException.Code.PLAYER_UNKNOWN));
            if (player.isMember()) {
                throw new GovernanceException(GovernanceException.Code.PAUSE_ALREADY_MEMBER);
            }
            if (repository.activePause(playerId, now).isPresent()) {
                throw new GovernanceException(GovernanceException.Code.PAUSE_ALREADY_ACTIVE);
            }
            return repository.createPause(playerId, reason, now, endsAt, appealPath, imposedBy);
        } catch (GovernanceException error) {
            throw error;
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not create promotion pause", error);
        }
    }

    public boolean liftPromotionPause(
            UUID playerId,
            String liftedBy,
            String reason
    ) throws GovernanceException {
        try {
            return repository.liftActivePause(playerId, clock.instant(), liftedBy, reason);
        } catch (GovernanceRepositoryException | RuntimeException error) {
            throw storageFailure("Could not lift promotion pause", error);
        }
    }

    private LocalDate today() {
        return clock.instant().atZone(GOVERNANCE_ZONE).toLocalDate();
    }

    private static GovernanceException storageFailure(String message, Throwable error) {
        return new GovernanceException(message, error);
    }
}
