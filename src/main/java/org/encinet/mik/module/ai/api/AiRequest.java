package org.encinet.mik.module.ai.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Platform-neutral request identity and conversation scope. */
public record AiRequest(
        String conversationId,
        String requesterName,
        String language,
        Optional<UUID> playerId,
        String prompt
) {
    public AiRequest {
        conversationId = requireText(conversationId, "conversationId");
        requesterName = requireText(requesterName, "requesterName");
        language = requireText(language, "language");
        playerId = Objects.requireNonNull(playerId, "playerId");
        prompt = Objects.requireNonNullElse(prompt, "");
    }

    private static String requireText(String value, String field) {
        String result = Objects.requireNonNull(value, field).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return result;
    }
}
