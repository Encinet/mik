package org.encinet.mik.module.governance.membership.profile;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PlayerProfile(
        UUID playerId,
        String playerName,
        Instant firstJoinedAt,
        Instant memberSince,
        Instant lastSuccessfulLoginAt
) {
    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("playerName must not be blank");
        }
        playerName = playerName.strip();
        Objects.requireNonNull(firstJoinedAt, "firstJoinedAt");
        Objects.requireNonNull(lastSuccessfulLoginAt, "lastSuccessfulLoginAt");
    }

    public boolean isMember() {
        return memberSince != null;
    }
}
