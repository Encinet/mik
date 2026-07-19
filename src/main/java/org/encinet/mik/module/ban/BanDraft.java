package org.encinet.mik.module.ban;

import java.time.Instant;
import java.util.UUID;

record BanDraft(
        UUID playerUuid,
        String playerName,
        String normalizedName,
        String reason,
        String source,
        Instant createdAt,
        Instant expiresAt,
        BanRecord.Origin origin
) {
}
