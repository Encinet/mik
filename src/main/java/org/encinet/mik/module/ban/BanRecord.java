package org.encinet.mik.module.ban;

import java.time.Instant;
import java.util.UUID;

public record BanRecord(
        long id,
        UUID playerUuid,
        String playerName,
        String normalizedName,
        String reason,
        String source,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        Instant revokedAt,
        Origin origin
) {

    public boolean isActive(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public Status statusAt(Instant now) {
        if (revokedAt != null) {
            return Status.REVOKED;
        }
        return expiresAt == null || expiresAt.isAfter(now) ? Status.ACTIVE : Status.EXPIRED;
    }

    public enum Origin {
        MIK,
        PAPER_IMPORT,
        GRIEFER,
        EXTERNAL_PLUGIN
    }

    public enum Status {
        ACTIVE,
        EXPIRED,
        REVOKED
    }
}
