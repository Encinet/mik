package org.encinet.mik.module.governance.membership.tenure;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ModeratorPresence(UUID playerId, String playerName, Instant lastSuccessfulLoginAt) {
    public ModeratorPresence {
        Objects.requireNonNull(playerId, "playerId");
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("playerName must not be blank");
        }
        playerName = playerName.strip();
        Objects.requireNonNull(lastSuccessfulLoginAt, "lastSuccessfulLoginAt");
    }
}
