package org.encinet.mik.feature.community.chat.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Accepted message and explicit responsibility for its game delivery. */
public record ChatDelivery(ChatMessage message, ChatSender target, String requestId, GameDelivery game) {
    public ChatDelivery {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(game, "game");
        if (message.submission().origin() instanceof ChatOrigin.Plugin
                || (message.submission().origin() instanceof ChatOrigin.Social
                && message.submission().conversation().channel() != ChatChannel.PUBLIC))
            throw new IllegalArgumentException("User delivery requires an admitted source and conversation");
        ChatAudience.of(message, target);
        if (requestId != null && (!(message.submission().origin() instanceof ChatOrigin.Web)
                || !requestId.matches("[A-Za-z0-9_-]{16,64}")))
            throw new IllegalArgumentException("Correlation requires a web request");
        if (game instanceof Native && !(message.submission().origin() instanceof ChatOrigin.Minecraft))
            throw new IllegalArgumentException("Only native Minecraft events own game delivery");
    }
    public static ChatDelivery managed(ChatMessage message, ChatSender target, String requestId) {
        return new ChatDelivery(message, target, requestId, new Managed());
    }
    public ChatAudience audience() { return ChatAudience.of(message, target); }
    public static ChatDelivery nativeGame(ChatMessage message, ChatSender target, Set<UUID> viewers, Set<UUID> channelAudience) {
        return new ChatDelivery(message, target, null, new Native(viewers, channelAudience));
    }
    public sealed interface GameDelivery permits Managed, Native { }
    public record Managed() implements GameDelivery { }
    public record Native(Set<UUID> viewers, Set<UUID> channelAudience) implements GameDelivery {
        public Native { viewers = Set.copyOf(viewers); channelAudience = Set.copyOf(channelAudience); }
    }
}
