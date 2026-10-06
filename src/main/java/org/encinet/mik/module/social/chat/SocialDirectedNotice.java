package org.encinet.mik.module.social.chat;

import java.util.Objects;
import java.util.UUID;

/** A public notice addressed to one verified Minecraft player. */
public record SocialDirectedNotice(UUID playerId, String playerName, String body) {
    public SocialDirectedNotice {
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName").strip();
        body = Objects.requireNonNull(body, "body").strip();
        if (playerName.isEmpty() || playerName.length() > 64 || body.isEmpty()
                || body.length() > 500 || playerName.codePoints().anyMatch(Character::isISOControl)
                || body.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid directed notice");
        }
    }
}
