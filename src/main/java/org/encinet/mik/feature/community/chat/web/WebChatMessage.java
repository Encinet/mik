package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatSender;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Explicit wire projection of an accepted user message from any authenticated source. */
public record WebChatMessage(ChatMessage message, String requestId, ChatSender target) {
    public WebChatMessage {
        Objects.requireNonNull(message, "message");
        if (message.submission().origin() instanceof ChatOrigin.Plugin)
            throw new IllegalArgumentException("Server output uses its own projection");
        ChatAudience.of(message, target);
    }

    public String id() { return message.submission().id().value(); }
    public ChatChannel channel() { return message.submission().conversation().channel(); }
    public UUID senderUuid() { return message.submission().sender().minecraftId().orElse(null); }
    public String senderName() { return message.submission().sender().displayName(); }
    public UUID targetUuid() { return target == null ? null : target.minecraftId().orElseThrow(); }
    public String targetName() { return target == null ? "" : target.minecraftName(); }
    public ChatContent content() { return message.content(); }
    public JsonObject source() {
        JsonObject source = new JsonObject();
        switch (message.submission().origin()) {
            case ChatOrigin.Minecraft _ -> source.addProperty("kind", "game");
            case ChatOrigin.Web _ -> source.addProperty("kind", "web");
            case ChatOrigin.Social social -> {
                source.addProperty("kind", "social");
                source.addProperty("platformId", social.platformId());
                source.addProperty("platformName", social.platformName());
            }
            default -> throw new IllegalArgumentException("Unsupported user message origin");
        }
        return source;
    }
    public JsonObject sender() {
        ChatSender identity = message.submission().sender();
        JsonObject sender = new JsonObject();
        String key = identity.externalIdentity().map(external -> {
            var id = external.key();
            String scope = id.platform() + '\0' + id.issuer() + '\0' + id.scope() + '\0' + id.subject();
            return "external:" + UUID.nameUUIDFromBytes(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }).orElseGet(() -> identity.minecraftId().map(UUID::toString)
                .orElse("message:" + id()));
        sender.addProperty("id", key);
        sender.addProperty("name", senderName());
        identity.minecraftId().ifPresent(id -> {
            sender.addProperty("playerUuid", id.toString());
            sender.addProperty("playerName", identity.minecraftName());
        });
        return sender;
    }
    public long createdAt() { return message.submission().receivedAt().toEpochMilli(); }

    /** Explicit wire fields prevent internal nodes, item snapshots or actions from leaking. */
    public JsonObject payload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", "user");
        payload.addProperty("id", id());
        if (requestId != null) payload.addProperty("requestId", requestId);
        payload.addProperty("channel", channel().name().toLowerCase(Locale.ROOT));
        payload.add("sender", sender());
        if (targetUuid() != null) payload.addProperty("targetUuid", targetUuid().toString());
        payload.addProperty("targetName", targetName());
        payload.add("content", WebChatContentRenderer.render(content()));
        payload.add("source", source());
        payload.addProperty("createdAt", createdAt());
        return payload;
    }

    public boolean mentions(UUID viewer) {
        return message.effects().stream().anyMatch(effect ->
                effect instanceof org.encinet.mik.feature.community.chat.model.ChatEffect.Mentions mention
                        && (mention.broadcast() || mention.playerIds().contains(viewer)));
    }
}
