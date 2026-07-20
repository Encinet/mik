package org.encinet.mik.module.player;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PlayerAssociationLookup {

    List<PlayerAssociation> findAssociations(UUID playerId);

    record PlayerAssociation(
            UUID playerId,
            int sharedAddressCount,
            int subjectAddressCount,
            int candidateAddressCount,
            double rarityScore,
            List<Instant> matchedLastSeenAt
    ) {
        public PlayerAssociation {
            matchedLastSeenAt = List.copyOf(matchedLastSeenAt);
        }

        public double coverage() {
            int smallerHistory = Math.min(subjectAddressCount, candidateAddressCount);
            return smallerHistory == 0 ? 0.0D : (double) sharedAddressCount / smallerHistory;
        }

        public Instant latestMatchedAt() {
            return matchedLastSeenAt.isEmpty() ? Instant.EPOCH : matchedLastSeenAt.getFirst();
        }
    }
}
