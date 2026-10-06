package org.encinet.mik.module.governance.removal;

import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations owned by the removal-petition capability. */
public interface RemovalRepository {
    RemovalSponsorship sponsorRemoval(
            long petitionId,
            UUID subjectId,
            String subjectName,
            UUID sponsorId,
            Instant opensAt,
            int sponsorsRequired,
            Collection<PlayerProfile> voters
    ) throws GovernanceRepositoryException;

    Optional<RemovalPetition> gatheringRemovalPetition(UUID subjectId, Instant now)
            throws GovernanceRepositoryException;

    List<RemovalPetition> gatheringRemovalPetitions(Instant now)
            throws GovernanceRepositoryException;

    RemovalPetition createRemovalPetition(
            UUID subjectId,
            String subjectName,
            Instant createdAt,
            int sponsorsRequired
    ) throws GovernanceRepositoryException;

    List<UUID> removalSponsors(long petitionId) throws GovernanceRepositoryException;

    void removeRemovalSponsor(long petitionId, UUID sponsorId)
            throws GovernanceRepositoryException;
}
