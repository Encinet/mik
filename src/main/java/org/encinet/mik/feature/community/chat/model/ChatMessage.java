package org.encinet.mik.feature.community.chat.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Interpreted message, independent of input capabilities and delivery state. */
public record ChatMessage(
        ChatMessageId id,
        ChatOrigin origin,
        ChatSender sender,
        ChatChannel channel,
        ChatSender target,
        Instant createdAt,
        ChatContent content
) {
    public ChatMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(content, "content");
        if (origin instanceof ChatOrigin.Player) {
            if (sender.minecraftId().isEmpty() || sender.externalIdentity().isPresent())
                throw new IllegalArgumentException("Game or Web chat requires a player identity");
        } else if (origin instanceof ChatOrigin.Social social && (channel != ChatChannel.PUBLIC
                || sender.externalIdentity().filter(identity -> identity.key().platform().equals(social.platform())).isEmpty())) {
            throw new IllegalArgumentException("External chat requires an account and a public destination");
        }
        ChatAudience.of(channel, sender.minecraftId().orElse(null), playerId(target));
        if (content.plainText().isBlank()) {
            throw new IllegalArgumentException("processed chat content is blank");
        }
    }

    public ChatAudience audience() {
        return ChatAudience.of(channel, sender.minecraftId().orElse(null), targetId());
    }

    public UUID targetId() { return playerId(target); }

    public ChatMessage withContent(ChatContent body) {
        return new ChatMessage(id, origin, sender, channel, target, createdAt, body);
    }

    private static UUID playerId(ChatSender identity) {
        if (identity == null) return null;
        if (identity.externalIdentity().isPresent()) throw new IllegalArgumentException("Private target must be a player");
        return identity.minecraftId().orElseThrow(() -> new IllegalArgumentException("Private target requires a player identity"));
    }
}
