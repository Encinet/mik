package org.encinet.mik.feature.community.chat.model;

import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex;

import java.util.Objects;
import java.util.UUID;

/** One account across its game and browser connections. */
public record ChatParticipant(UUID playerUuid, String name, boolean game, int webConnections) {
    public ChatParticipant {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(name, "name");
        if (webConnections < 0 || (!game && webConnections == 0))
            throw new IllegalArgumentException("A chat participant requires an active connection");
    }

    public PlayerMentionIndex.Target mentionTarget() {
        return new PlayerMentionIndex.Target(playerUuid, name);
    }
}
