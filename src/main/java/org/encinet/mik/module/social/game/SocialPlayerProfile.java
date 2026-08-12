package org.encinet.mik.module.social.game;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Privacy-limited Minecraft profile associated with one authenticated social identity. */
public record SocialPlayerProfile(
        UUID playerId,
        String playerName,
        Presence presence,
        Duration playTime,
        Optional<Instant> firstJoined,
        Optional<Instant> lastSeen,
        Optional<SocialPlayerAvatar> avatar
) {
    public SocialPlayerProfile {
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName");
        presence = Objects.requireNonNull(presence, "presence");
        playTime = Objects.requireNonNull(playTime, "playTime");
        firstJoined = Objects.requireNonNull(firstJoined, "firstJoined");
        lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
        avatar = Objects.requireNonNull(avatar, "avatar");
        if (playTime.isNegative()) {
            throw new IllegalArgumentException("playTime must not be negative");
        }
    }

    public enum Presence {
        ONLINE,
        AFK,
        OFFLINE
    }
}
