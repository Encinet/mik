package org.encinet.mik.feature.community.chat.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Connection change, kept separate from user messages. */
public record ChatPresence(ChatMessageId id, Instant createdAt, UUID player, String name, boolean joined) {
    public ChatPresence {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(player, "player");
        if (name.isBlank() || name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid player name in presence notice");
    }
}
