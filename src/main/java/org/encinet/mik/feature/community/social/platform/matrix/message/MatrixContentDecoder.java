package org.encinet.mik.feature.community.social.platform.matrix.message;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Decodes stable Matrix room-message and sticker content, including event relationships. */
public final class MatrixContentDecoder {
    private MatrixContentDecoder() { }

    public static Optional<MatrixMessageContent> decode(String eventType, JsonObject content) {
        MatrixMessageContent.Kind kind;
        if ("m.sticker".equals(eventType)) {
            kind = MatrixMessageContent.Kind.STICKER;
        } else if ("m.room.message".equals(eventType)) {
            String type = string(content, "msgtype");
            if (type.isBlank()) {
                return Optional.empty();
            }
            kind = switch (type) {
                case "m.text" -> MatrixMessageContent.Kind.TEXT;
                case "m.notice" -> MatrixMessageContent.Kind.NOTICE;
                case "m.emote" -> MatrixMessageContent.Kind.EMOTE;
                case "m.image" -> MatrixMessageContent.Kind.IMAGE;
                case "m.audio" -> MatrixMessageContent.Kind.AUDIO;
                case "m.video" -> MatrixMessageContent.Kind.VIDEO;
                case "m.file" -> MatrixMessageContent.Kind.FILE;
                case "m.location" -> MatrixMessageContent.Kind.LOCATION;
                default -> MatrixMessageContent.Kind.UNKNOWN;
            };
        } else {
            return Optional.empty();
        }
        // body is the mandatory fallback, even for unknown msgtypes. Redacted events lack it.
        if (!content.has("body") || !content.get("body").isJsonPrimitive()
                || !content.get("body").getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        Optional<String> html = "org.matrix.custom.html".equals(string(content, "format"))
                && kind != MatrixMessageContent.Kind.LOCATION
                && kind != MatrixMessageContent.Kind.STICKER
                && kind != MatrixMessageContent.Kind.UNKNOWN
                ? optional(string(content, "formatted_body")) : Optional.empty();
        JsonObject relates = object(content, "m.relates_to");
        String relationType = string(relates, "rel_type");
        Optional<String> thread = "m.thread".equals(relationType)
                ? optional(string(relates, "event_id")) : Optional.empty();
        JsonElement fallback = relates == null ? null : relates.get("is_falling_back");
        MatrixMessageContent.Relation relation = new MatrixMessageContent.Relation(
                optional(string(object(relates, "m.in_reply_to"), "event_id")), thread,
                thread.isPresent() && fallback != null && fallback.isJsonPrimitive()
                        && fallback.getAsJsonPrimitive().isBoolean() && fallback.getAsBoolean(),
                "m.replace".equals(relationType));
        Set<String> mentions = new LinkedHashSet<>();
        JsonObject mentionData = object(content, "m.mentions");
        JsonElement users = mentionData == null ? null : mentionData.get("user_ids");
        if (users != null && users.isJsonArray()) {
            for (JsonElement user : users.getAsJsonArray()) {
                if (mentions.size() >= 32) {
                    break;
                }
                if (user.isJsonPrimitive() && user.getAsJsonPrimitive().isString()
                        && !user.getAsString().isBlank()) {
                    mentions.add(user.getAsString());
                }
            }
        }
        String filename = switch (kind) {
            case IMAGE, AUDIO, VIDEO, FILE -> string(content, "filename");
            default -> "";
        };
        return Optional.of(new MatrixMessageContent(kind, string(content, "body"), html,
                filename, mentions, relation));
    }

    private static Optional<String> optional(String value) {
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    private static JsonObject object(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
