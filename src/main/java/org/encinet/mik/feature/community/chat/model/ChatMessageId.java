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

    public static ChatMessageId external(String platformId, String conversationId, String eventId) {
        String scope = Objects.requireNonNull(platformId) + '\0' + Objects.requireNonNull(conversationId)
                + '\0' + Objects.requireNonNull(eventId);
        return new ChatMessageId("external:" + UUID.nameUUIDFromBytes(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
