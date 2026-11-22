package org.encinet.mik.feature.community.chat.model;

import java.util.Objects;
import java.util.UUID;

/** Stable internal message correlation identifier. */
public record ChatMessageId(String value) {
    public ChatMessageId {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty() || value.length() > 128
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("chat message ID is invalid");
        }
    }

    public static ChatMessageId random() {
        return new ChatMessageId(UUID.randomUUID().toString());
    }

}
