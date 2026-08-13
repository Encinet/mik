package org.encinet.mik.module.chat.model;

import java.util.Set;
import java.util.UUID;

/** Semantic post-commit side effects derived from processed content. */
public sealed interface ChatEffect permits ChatEffect.Mentions {
    record Mentions(Set<UUID> playerIds, boolean broadcast) implements ChatEffect {
        public Mentions {
            playerIds = Set.copyOf(playerIds == null ? Set.of() : playerIds);
        }
    }
}
