package org.encinet.mik.feature.community.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A durable association between a Minecraft account and one platform principal. */
public record IdentityBinding(
        UUID bindingId,
        UUID playerId,
        String playerName,
        ExternalIdentityKey externalKey,
        String externalDisplayName,
        String scopeDisplayName,
        Instant createdAt,
        Instant verifiedAt
) {

    private static final int MAX_PLAYER_NAME_LENGTH = 64;

    public IdentityBinding {
        bindingId = Objects.requireNonNull(bindingId, "bindingId");
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = requirePlayerName(playerName);
        externalKey = Objects.requireNonNull(externalKey, "externalKey");
        ExternalIdentity identity = new ExternalIdentity(externalKey, externalDisplayName, scopeDisplayName);
        externalDisplayName = identity.displayName();
        scopeDisplayName = identity.scopeDisplayName();
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
        if (verifiedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("verifiedAt must not precede createdAt");
        }
    }

    static String requirePlayerName(String value) {
        String playerName = Objects.requireNonNull(value, "playerName");
        if (playerName.isBlank() || playerName.length() > MAX_PLAYER_NAME_LENGTH) {
            throw new IllegalArgumentException("playerName has an invalid length");
        }
        playerName.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException(
                        "playerName must not contain control characters");
            }
        });
        return playerName;
    }
}
