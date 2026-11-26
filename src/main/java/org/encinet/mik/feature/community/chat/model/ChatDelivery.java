package org.encinet.mik.feature.community.chat.model;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Live delivery constraints; neither a draft nor part of the stored message. */
public record ChatDelivery(ChatMessage message, Optional<Set<UUID>> visibility, ChatMentions mentions) {
    public ChatDelivery {
        Objects.requireNonNull(message, "message");
        visibility = visibility.map(Set::copyOf);
        Objects.requireNonNull(mentions, "mentions");
    }
}
