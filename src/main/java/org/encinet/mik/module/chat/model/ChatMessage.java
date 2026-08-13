package org.encinet.mik.module.chat.model;

import java.util.Objects;
import java.util.Set;

/** Processed semantic message consumed by every transport renderer. */
public record ChatMessage(
        ChatSubmission submission,
        ChatContent content,
        Set<ChatEffect> effects
) {
    public ChatMessage {
        submission = Objects.requireNonNull(submission, "submission");
        content = Objects.requireNonNull(content, "content");
        effects = Set.copyOf(Objects.requireNonNullElse(effects, Set.of()));
        if (content.plainText().isBlank()) {
            throw new IllegalArgumentException("processed chat content is blank");
        }
    }
}
