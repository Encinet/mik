package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatServerMessage;

import java.util.Locale;
import java.util.UUID;

/** Explicit server-output projection, including viewer-relative private conversation metadata. */
final class WebChatServerMessage {
    private WebChatServerMessage() { }

    static JsonObject payload(ChatServerMessage message, UUID viewer) {
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", "server");
        payload.addProperty("id", message.id().value());
        payload.addProperty("channel", message.conversation().channel().name().toLowerCase(Locale.ROOT));
        payload.addProperty("createdAt", message.createdAt().toEpochMilli());
        payload.addProperty("countsAsUnread", message.countsAsUnread());
        message.subject().ifPresent(subject -> {
            JsonObject identity = new JsonObject();
            identity.addProperty("playerUuid", subject.playerUuid().toString());
            identity.addProperty("name", subject.name());
            payload.add("subject", identity);
        });
        if (message.conversation().channel() == ChatChannel.PRIVATE) {
            if (!message.participants().containsKey(viewer)) throw new IllegalArgumentException("Viewer is outside the private conversation");
            UUID peer = message.participants().keySet().stream().filter(id -> !id.equals(viewer)).findFirst().orElseThrow();
            payload.addProperty("recipientUuid", viewer.toString());
            payload.addProperty("targetUuid", peer.toString());
            payload.addProperty("targetName", message.participants().get(peer));
        }
        switch (message.content()) {
            case ChatServerMessage.RichText text -> payload.add("content", WebChatContentRenderer.render(text.content()));
            case ChatServerMessage.Translation translation -> {
                JsonObject content = new JsonObject(), values = new JsonObject();
                content.addProperty("template", translation.template().key());
                translation.values().forEach(values::addProperty);
                content.add("values", values);
                payload.add("content", content);
            }
        }
        return payload;
    }
}
