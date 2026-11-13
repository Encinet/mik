package org.encinet.mik.feature.community.chat.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.feature.community.chat.history.ChatHistoryStore;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatEffect;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatPlaceholder;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatReferences;
import org.encinet.mik.feature.community.chat.model.ChatServerMessage;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.shared.i18n.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Versioned, safe presentation snapshots; never stores game items, credentials or raw platform events. */
final class WebChatHistory {
    private static final Gson JSON = new Gson();
    private WebChatHistory() { }

    /** Detach processing inventory and item binaries before retaining a message in the bounded queue. */
    static ChatDelivery snapshot(ChatDelivery delivery) {
        var source = delivery.message().submission();
        var submission = new ChatSubmission(source.id(), source.origin(), source.sender(), source.conversation(),
                source.sourceText(), ChatReferences.empty(), source.receivedAt(), ChatProcessingContext.external());
        var message = new ChatMessage(submission, snapshot(delivery.message().content()), delivery.message().effects());
        return ChatDelivery.managed(message, delivery.target(), null);
    }

    static ChatServerMessage snapshot(ChatServerMessage message) {
        var content = message.content() instanceof ChatServerMessage.RichText text
                ? new ChatServerMessage.RichText(snapshot(text.content())) : message.content();
        return new ChatServerMessage(message.id(), message.conversation(), content, message.subject(),
                message.participants(), message.countsAsUnread(), message.createdAt());
    }

    private static ChatContent snapshot(ChatContent content) {
        return new ChatContent(content.nodes().stream().map(node -> node instanceof ChatNode.Item item
                ? new ChatNode.Item(item.fallback(), Optional.empty(), item.style()) : node).toList());
    }

    static ChatHistoryStore.Entry user(ChatDelivery delivery) {
        WebChatMessage message = new WebChatMessage(delivery.message(), null, delivery.target());
        JsonObject payload = message.payload();
        payload.add("content", WebChatContentRenderer.archive(delivery.message().content()));
        JsonObject snapshot = snapshot(payload);
        var mentions = new com.google.gson.JsonArray();
        for (ChatEffect effect : delivery.message().effects()) {
            if (effect instanceof ChatEffect.Mentions mention) {
                mention.playerIds().forEach(id -> mentions.add(id.toString()));
                if (mention.broadcast()) snapshot.addProperty("broadcastMention", true);
            }
        }
        snapshot.add("mentions", mentions);
        return new ChatHistoryStore.Entry(message.id(), delivery.audience(), Optional.empty(), serialize(snapshot, delivery.message().content()));
    }

    static ChatHistoryStore.Entry server(ChatServerMessage message, Optional<Set<UUID>> viewers) {
        UUID first = message.participants().keySet().stream().findFirst().orElse(null);
        JsonObject payload = WebChatServerMessage.payload(message, first);
        if (message.content() instanceof ChatServerMessage.RichText text)
            payload.add("content", WebChatContentRenderer.archive(text.content()));
        JsonObject snapshot = snapshot(payload);
        JsonObject names = new JsonObject();
        message.participants().forEach((id, name) -> names.addProperty(id.toString(), name));
        snapshot.add("participants", names);
        return new ChatHistoryStore.Entry(message.id().value(), message.audience(), viewers,
                serialize(snapshot, message.content() instanceof ChatServerMessage.RichText text ? text.content() : null));
    }

    private static String serialize(JsonObject snapshot, org.encinet.mik.feature.community.chat.model.ChatContent content) {
        String encoded = JSON.toJson(snapshot);
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ChatHistoryStore.PAGE_BYTES - 4096 && content != null) {
            snapshot.getAsJsonObject("message").add("content", WebChatContentRenderer.render(
                    org.encinet.mik.feature.community.chat.model.ChatContent.plain(content.plainText())));
            encoded = JSON.toJson(snapshot);
        }
        return encoded;
    }

    private static JsonObject snapshot(JsonObject payload) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("version", 1);
        snapshot.add("message", payload);
        return snapshot;
    }

    record Presentation(JsonArray messages, java.util.List<String> compressionScopes, boolean hasMore, long nextBefore) { }

    /** Captured on the game thread; all presentation work can then run without server objects or language services. */
    record Viewer(UUID id, Set<UUID> hidden, Map<Message, String> descriptions) {
        Viewer {
            Objects.requireNonNull(id);
            hidden = Set.copyOf(hidden);
            descriptions = Map.copyOf(descriptions);
        }
    }

    /** Metadata and different access scopes never share a compression dictionary. */
    static java.util.List<String> responseParts(ChatHistoryStore.SyncPage result, ChatChannel channel, String request, Viewer viewer) {
        var page = result.page();
        var presented = presentPage(page, viewer.id(), id -> !viewer.hidden().contains(id), viewer.descriptions()::get);
        JsonObject response = new JsonObject();
        response.addProperty("type", "history");
        response.addProperty("requestId", request);
        response.addProperty("channel", channel.name().toLowerCase(Locale.ROOT));
        response.addProperty("checkpoint", result.checkpoint());
        if (presented.hasMore()) response.addProperty("nextPage", result.continuation().apply(presented.nextBefore()));
        response.addProperty("reset", result.reset());
        String metadata = JSON.toJson(response);
        var parts = new ArrayList<String>();
        parts.add(metadata.substring(0, metadata.length() - 1) + ",\"messages\":[");
        String previousScope = null;
        var group = new StringBuilder();
        for (int index = 0; index < presented.messages().size(); index++) {
            String scope = presented.compressionScopes().get(index);
            if (!scope.equals(previousScope) && !group.isEmpty()) {
                parts.add(group.toString());
                group.setLength(0);
            }
            group.append(index == 0 ? "" : ",").append(JSON.toJson(presented.messages().get(index)));
            previousScope = scope;
        }
        if (!group.isEmpty()) parts.add(group.toString());
        parts.add("]}");
        return parts;
    }

    /** Localized text can be larger than its archive. Only advance past rows actually included or hidden. */
    static Presentation presentPage(ChatHistoryStore.Page page, UUID viewer, Predicate<UUID> visible,
                                    Function<Message, String> text) {
        var selected = new ArrayList<JsonObject>();
        var scopes = new ArrayList<String>();
        int bytes = 2;
        long before = page.nextBefore();
        boolean more = page.hasMore();
        for (var row : page.rows()) {
            var message = present(row, viewer, visible, text);
            if (message.isPresent()) {
                int size = JSON.toJson(message.get()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
                if (bytes + size > ChatHistoryStore.PAGE_BYTES) {
                    if (selected.isEmpty()) throw new IllegalArgumentException("History message exceeds page budget");
                    more = true;
                    break;
                }
                selected.add(message.get());
                scopes.add(row.compressionScope());
                bytes += size;
            }
            before = row.cursor();
        }
        JsonArray messages = new JsonArray();
        selected.reversed().forEach(messages::add);
        return new Presentation(messages, java.util.List.copyOf(scopes.reversed()), more, before);
    }

    /** Uses only the captured viewer's visibility and translated descriptions. */
    static Optional<JsonObject> present(ChatHistoryStore.Row row, UUID viewer, Predicate<UUID> visible,
                                       Function<Message, String> text) {
        JsonObject snapshot = JsonParser.parseString(row.payload()).getAsJsonObject();
        if (snapshot.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported history snapshot");
        JsonObject payload = snapshot.getAsJsonObject("message");
        payload.addProperty("order", row.order());
        payload.addProperty("expiresAt", row.expiresAt());
        if (payload.has("subject") && !visible.test(UUID.fromString(
                payload.getAsJsonObject("subject").get("playerUuid").getAsString()))) return Optional.empty();
        JsonObject participants = snapshot.getAsJsonObject("participants");
        if (participants != null && !participants.isEmpty()) {
            if (participants.keySet().stream().map(UUID::fromString).anyMatch(id -> !visible.test(id))) return Optional.empty();
            if (!participants.has(viewer.toString())) return Optional.empty();
            String peer = participants.keySet().stream().filter(id -> !id.equals(viewer.toString())).findFirst().orElseThrow();
            payload.addProperty("recipientUuid", viewer.toString());
            payload.addProperty("targetUuid", peer);
            payload.addProperty("targetName", participants.get(peer).getAsString());
        }
        if (payload.get("kind").getAsString().equals("user")) {
            String sender = payload.getAsJsonObject("sender").has("playerUuid")
                    ? payload.getAsJsonObject("sender").get("playerUuid").getAsString() : "";
            if (payload.get("channel").getAsString().equals("private")
                    && (!visible.test(UUID.fromString(sender))
                    || !visible.test(UUID.fromString(payload.get("targetUuid").getAsString())))) return Optional.empty();
            Set<String> mentions = new HashSet<>();
            snapshot.getAsJsonArray("mentions").forEach(id -> mentions.add(id.getAsString()));
            payload.addProperty("mentioned", !sender.equals(viewer.toString()) && (mentions.contains(viewer.toString())
                    || snapshot.has("broadcastMention") && snapshot.get("broadcastMention").getAsBoolean()));
        }
        if (payload.get("content").isJsonArray()) {
            int length = 0;
            for (var element : payload.getAsJsonArray("content")) length += element.getAsJsonObject().get("text").getAsString().length();
            for (var element : payload.getAsJsonArray("content")) {
                JsonObject part = element.getAsJsonObject();
                if (part.has("placeholder")) {
                    ChatPlaceholder placeholder = ChatPlaceholder.valueOf(part.remove("placeholder").getAsString());
                    String translated = text.apply(placeholder.message());
                    int change = translated.length() - part.get("text").getAsString().length();
                    if (length + change <= 16_000) {
                        part.addProperty("text", translated);
                        length += change;
                    }
                }
            }
            if (JSON.toJson(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ChatHistoryStore.PAGE_BYTES - 4096) {
                StringBuilder plain = new StringBuilder();
                payload.getAsJsonArray("content").forEach(part -> plain.append(part.getAsJsonObject().get("text").getAsString()));
                payload.add("content", WebChatContentRenderer.render(
                        org.encinet.mik.feature.community.chat.model.ChatContent.plain(plain.toString())));
            }
        }
        return Optional.of(payload);
    }
}
