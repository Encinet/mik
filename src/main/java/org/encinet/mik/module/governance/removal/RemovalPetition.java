package org.encinet.mik.module.governance.removal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RemovalPetition(
        long id,
        UUID subjectId,
        String subjectName,
        Instant createdAt,
        Instant expiresAt,
        int sponsorsRequired,
        int sponsorCount,
        Long voteId
) {
    public RemovalPetition {
        Objects.requireNonNull(subjectId, "subjectId");
        if (subjectName == null || subjectName.isBlank()) {
            throw new IllegalArgumentException("subjectName must not be blank");
        }
        subjectName = subjectName.strip();
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean started() {
        return voteId != null;
    }
}
