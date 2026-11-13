package org.encinet.mik.feature.community.social.platform.qq.gateway;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.social.platform.qq.message.QqContentDecoder;
import org.encinet.mik.feature.community.social.platform.qq.message.QqMessageContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Minimal trusted fields from a QQ Gateway group-message dispatch. */
public record QqGatewayGroupMessage(
        String eventId,
        String messageId,
        String groupOpenId,
        String authorName,
        String memberOpenId,
        boolean authorIsBot,
        QqMessageContent content,
        List<MemberReference> mentions,
        Optional<MemberReference> repliedAuthor
) {
    public QqGatewayGroupMessage {
        java.util.Objects.requireNonNull(content, "content");
        mentions = List.copyOf(mentions);
        repliedAuthor = repliedAuthor == null ? Optional.empty() : repliedAuthor;
    }

    public static Optional<QqGatewayGroupMessage> from(JsonElement payload, String eventId) {
        if (payload == null || !payload.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject data = payload.getAsJsonObject();
        String messageId = string(data, "id");
        String groupOpenId = string(data, "group_openid");
        if (messageId.isBlank() || groupOpenId.isBlank()) {
            return Optional.empty();
        }

        JsonObject author = object(data, "author");
        String authorName = author == null ? "" : string(author, "username");
        String memberOpenId = author == null ? "" : string(author, "member_openid");
        boolean authorIsBot = author != null && bool(author, "bot");
        List<MemberReference> mentions = mentions(data);
        Optional<MemberReference> repliedAuthor = repliedAuthor(data);
        String stableEventId = eventId == null || eventId.isBlank() ? messageId : eventId;
        return Optional.of(new QqGatewayGroupMessage(stableEventId, messageId,
                groupOpenId, cleanSingleLine(authorName, 64), memberOpenId, authorIsBot,
                QqContentDecoder.decode(data),
                mentions, repliedAuthor));
    }

    private static List<MemberReference> mentions(JsonObject data) {
        JsonArray values = array(data, "mentions");
        if (values == null) {
            return List.of();
        }
        List<MemberReference> references = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonObject() || references.size() >= 32) {
                continue;
            }
            JsonObject mention = value.getAsJsonObject();
            String tokenId = string(mention, "id");
            String memberOpenId = string(mention, "member_openid");
            if (tokenId.isBlank() && memberOpenId.isBlank()) {
                continue;
            }
            references.add(new MemberReference(tokenId, memberOpenId,
                    cleanSingleLine(string(mention, "username"), 64),
                    bool(mention, "bot"), bool(mention, "is_you")));
        }
        return List.copyOf(references);
    }

    private static Optional<MemberReference> repliedAuthor(JsonObject data) {
        JsonArray elements = array(data, "msg_elements");
        if (elements == null) {
            return Optional.empty();
        }
        QqMessageContent.Type messageType = QqContentDecoder.messageType(data);
        if (messageType.isCollection()) return Optional.empty();
        int inspected = 0;
        for (JsonElement value : elements) {
            if (++inspected > 32) break;
            if (!value.isJsonObject()) {
                continue;
            }
            JsonObject element = value.getAsJsonObject();
            if (QqContentDecoder.isHistoryElement(element)) continue;
            if (messageType != QqMessageContent.Type.REPLY
                    && QqContentDecoder.messageType(element) != QqMessageContent.Type.REPLY) continue;
            JsonObject author = object(element, "author");
            if (author == null || string(author, "member_openid").isBlank()) {
                continue;
            }
            return Optional.of(new MemberReference("",
                    string(author, "member_openid"),
                    cleanSingleLine(string(author, "username"), 64),
                    bool(author, "bot"), bool(author, "is_you")));
        }
        return Optional.empty();
    }

    private static String cleanMessage(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(Math.min(value.length(), 2_000));
        boolean lastWasWhitespace = false;
        for (int index = 0; index < value.length() && result.length() < 2_000; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character)) {
                if (!lastWasWhitespace && !result.isEmpty()) {
                    result.append(' ');
                }
                lastWasWhitespace = true;
            } else {
                result.append(character);
                lastWasWhitespace = false;
            }
        }
        if (!result.isEmpty() && Character.isHighSurrogate(result.charAt(result.length() - 1))) {
            result.setLength(result.length() - 1);
        }
        return result.toString().strip();
    }

    private static String cleanSingleLine(String value, int maximumLength) {
        String clean = cleanMessage(value);
        if (clean.length() <= maximumLength) {
            return clean;
        }
        int end = maximumLength;
        if (end > 0 && Character.isHighSurrogate(clean.charAt(end - 1))) {
            end--;
        }
        return clean.substring(0, end);
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean bool(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            return false;
        }
        try {
            return value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static JsonObject object(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    public record MemberReference(
            String tokenId,
            String memberOpenId,
            String username,
            boolean bot,
            boolean isYou
    ) {
        public MemberReference {
            tokenId = tokenId == null ? "" : tokenId;
            memberOpenId = memberOpenId == null ? "" : memberOpenId;
            username = username == null ? "" : username;
        }
    }
}
