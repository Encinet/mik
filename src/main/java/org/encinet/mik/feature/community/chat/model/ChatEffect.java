package org.encinet.mik.feature.community.chat.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Semantic post-commit side effects derived from processed content. */
public sealed interface ChatEffect permits ChatEffect.Mentions {
    static Set<ChatEffect> fromContent(ChatContent content) {
        var players = new HashSet<UUID>();
        boolean broadcast = false;
        for (ChatNode node : content.nodes()) {
            if (node instanceof ChatNode.PlayerMention mention) {
                players.add(mention.playerId());
            } else if (node instanceof ChatNode.BroadcastMention) {
                broadcast = true;
            }
        }
        return players.isEmpty() && !broadcast ? Set.of() : Set.of(new Mentions(players, broadcast));
    }

    record Mentions(Set<UUID> playerIds, boolean broadcast) implements ChatEffect {
        public Mentions {
            playerIds = Set.copyOf(playerIds == null ? Set.of() : playerIds);
        }
    }
}
