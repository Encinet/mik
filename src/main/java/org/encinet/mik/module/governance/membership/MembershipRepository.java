package org.encinet.mik.module.governance.membership;

import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.membership.participation.DailyParticipation;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.membership.promotion.PromotionPause;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations owned by the membership capability. */
public interface MembershipRepository {
    void observePlayer(
            UUID playerId,
            String playerName,
            Instant firstJoinedAt,
            Instant lastLoginAt
    ) throws GovernanceRepositoryException;

    void recordMemberSince(UUID playerId, Instant memberSince)
            throws GovernanceRepositoryException;

    Optional<PlayerProfile> player(UUID playerId) throws GovernanceRepositoryException;

    List<PlayerProfile> players() throws GovernanceRepositoryException;

    void addParticipation(UUID playerId, LocalDate date, long seconds)
            throws GovernanceRepositoryException;

    List<DailyParticipation> participation(UUID playerId)
            throws GovernanceRepositoryException;

    PromotionPause createPause(
            UUID playerId,
            String reason,
            Instant startsAt,
            Instant endsAt,
            String appealPath,
            String imposedBy
    ) throws GovernanceRepositoryException;

    Optional<PromotionPause> activePause(UUID playerId, Instant now)
            throws GovernanceRepositoryException;

    boolean liftActivePause(
            UUID playerId,
            Instant now,
            String liftedBy,
            String liftReason
    ) throws GovernanceRepositoryException;
}
