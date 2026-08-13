package org.encinet.mik.module.social.chat;

import org.encinet.mik.module.identity.ExternalIdentity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bound target identities that may represent one Minecraft player on a platform. */
public record SocialChatMentionRequest(
        UUID playerId,
        String playerName,
        List<ExternalIdentity> candidates
) {
    public SocialChatMentionRequest {
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName").strip();
        if (playerName.isEmpty() || playerName.length() > 64) {
            throw new IllegalArgumentException("playerName is invalid");
        }
        LinkedHashMap<Object, ExternalIdentity> unique = new LinkedHashMap<>();
        Objects.requireNonNullElse(candidates, List.<ExternalIdentity>of())
                .forEach(identity -> unique.putIfAbsent(
                        Objects.requireNonNull(identity, "candidate").key(), identity));
        candidates = List.copyOf(unique.values());
    }
}
