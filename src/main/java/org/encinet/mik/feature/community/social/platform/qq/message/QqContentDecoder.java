package org.encinet.mik.feature.community.social.platform.qq.message;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.encinet.mik.feature.community.social.platform.qq.message.QqMessageContent.*;

/** QQ receive protocol decoder. Wire syntax and JSON fields do not escape this layer. */
public final class QqContentDecoder {
    private static final int MAXIMUM_ELEMENTS = 64;
    private static final int MAXIMUM_DEPTH = 4;
    private static final int MAXIMUM_INLINE_ELEMENTS = 512;
    private static final Pattern INLINE = Pattern.compile(
            "<@!?([^>\\s]+)>|<qqbot-at-user\\s+id=\"([^\"]+)\"\\s*/>"
                    + "|<faceType=\\d+,faceId=\"\\d*\"(?:,ext=\"[^\"<>]*\")?>|<emoji:[A-Za-z0-9_]+>");

    private int elements;
    private int inlineElements;
    private boolean truncated;

    private QqContentDecoder() { }

    public static QqMessageContent decode(JsonObject data) {
        QqContentDecoder decoder = new QqContentDecoder();
        QqMessageContent message = decoder.message(data, 0);
        return new QqMessageContent(message.type(), message.text(), message.elements(), decoder.truncated);
    }

    public static Type messageType(JsonObject data) {
        if (!data.has("message_type")) return Type.TEXT;
        long code = number(data, "message_type");
        if (code == 0) return Type.TEXT;
        if (code == 3) return Type.CARD;
        if (code == 101) return Type.COMPOSITE;
        if (code == 102) return Type.HISTORY;
        if (code == 103) return Type.REPLY;
        return Type.UNKNOWN;
    }

    private QqMessageContent message(JsonObject data, int depth) {
        elements++;
        String content = string(data, "content");
        if (content.isBlank()) content = string(object(data, "markdown"), "content");
        List<Inline> text = inline(content);
        List<Element> children = new ArrayList<>();
        JsonArray attachments = array(data, "attachments");
        if (attachments != null) for (JsonElement value : attachments) {
            if (!take()) break;
            if (value.isJsonObject()) media(value.getAsJsonObject()).ifPresent(children::add);
        }
        JsonObject card = object(data, "ark_data");
        if (card != null && take()) children.add(card(card));
        JsonArray nested = array(data, "msg_elements");
        if (nested != null) for (JsonElement value : nested) {
            if (depth >= MAXIMUM_DEPTH || elements >= MAXIMUM_ELEMENTS) { truncated = true; break; }
            if (!value.isJsonObject()) { elements++; continue; }
            JsonObject child = value.getAsJsonObject();
            children.add(new Nested(string(object(child, "author"), "username"),
                    message(child, depth + 1)));
        }
        return new QqMessageContent(messageType(data), text, children, false);
    }

    private boolean take() {
        if (elements >= MAXIMUM_ELEMENTS) { truncated = true; return false; }
        elements++;
        return true;
    }

    private List<Inline> inline(String source) {
        List<Inline> result = new ArrayList<>();
        Matcher matcher = INLINE.matcher(source);
        int previous = 0;
        while (matcher.find()) {
            if (!text(result, source.substring(previous, matcher.start()))) return result;
            if (inlineElements++ >= MAXIMUM_INLINE_ELEMENTS) { truncated = true; return result; }
            String id = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            result.add(id == null ? new Placeholder(Kind.FACE) : new Mention(id));
            previous = matcher.end();
        }
        text(result, source.substring(previous));
        return result;
    }

    private boolean text(List<Inline> result, String value) {
        if (value.isEmpty()) return true;
        if (inlineElements++ >= MAXIMUM_INLINE_ELEMENTS) { truncated = true; return false; }
        if (value.length() > 8_000) truncated = true;
        result.add(new Text(bounded(value, 8_000)));
        return true;
    }

    private Optional<Media> media(JsonObject data) {
        String mime = string(data, "content_type").toLowerCase(Locale.ROOT);
        if (mime.isBlank() && string(data, "filename").isBlank()
                && string(data, "url").isBlank() && string(data, "voice_wav_url").isBlank()) return Optional.empty();
        Kind kind = mime.equals("voice") || mime.startsWith("audio/") ? Kind.AUDIO
                : mime.startsWith("image/") ? Kind.IMAGE : mime.startsWith("video/") ? Kind.VIDEO : Kind.FILE;
        Optional<URI> url = kind == Kind.AUDIO ? safeUrl(string(data, "voice_wav_url")) : Optional.empty();
        if (url.isEmpty()) url = safeUrl(string(data, "url"));
        return Optional.of(new Media(kind, string(data, "filename"),
                number(data, "width"), number(data, "height"), number(data, "size"), url,
                kind == Kind.AUDIO ? string(data, "asr_refer_text") : ""));
    }

    private Card card(JsonObject data) {
        Kind kind = switch (string(data, "ark_type")) {
            case "map" -> Kind.LOCATION;
            case "contact_card" -> Kind.CONTACT;
            case "video_share" -> Kind.VIDEO;
            case "music_together" -> Kind.MUSIC;
            case "picture" -> Kind.IMAGE;
            default -> Kind.CARD;
        };
        JsonObject fields = object(data, "fields");
        var captions = new LinkedHashSet<String>();
        for (String key : new String[]{"title", "desc", "nickname", "address", "source", "tag"}) {
            caption(captions, string(fields, key));
        }
        JsonArray tags = array(fields, "tags");
        if (tags == null) caption(captions, string(fields, "tags"));
        else for (int index = 0; index < Math.min(4, tags.size()); index++) {
            JsonElement tag = tags.get(index);
            if (tag.isJsonPrimitive() && tag.getAsJsonPrimitive().isString()) caption(captions, tag.getAsString());
        }
        caption(captions, string(data, "prompt"));
        if (captions.isEmpty()) caption(captions, string(data, "ark_name"));
        return new Card(kind, List.copyOf(captions), safeUrl(string(fields, "jump_url")),
                safeUrl(string(fields, "preview")));
    }

    private static void caption(LinkedHashSet<String> captions, String value) {
        if (!value.isBlank()) captions.add(value);
    }

    private static Optional<URI> safeUrl(String value) {
        if (value.isBlank() || value.length() > 8_000 || value.codePoints().anyMatch(point ->
                Character.isISOControl(point) || Character.isWhitespace(point)
                        || Character.getType(point) == Character.FORMAT)) return Optional.empty();
        try {
            URI url = URI.create(value.startsWith("//") ? "https:" + value : value);
            if (url.getHost() == null || url.getRawUserInfo() != null || url.getRawAuthority().indexOf('\\') >= 0
                    || !("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))) {
                return Optional.empty();
            }
            return Optional.of(url);
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    private static String bounded(String value, int limit) {
        if (value.length() <= limit) return value;
        int end = Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit;
        return value.substring(0, end);
    }

    private static String string(JsonObject data, String key) {
        JsonElement value = data == null ? null : data.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    private static long number(JsonObject data, String key) {
        JsonElement value = data == null ? null : data.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return -1;
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException | NumberFormatException invalid) { return -1; }
    }

    private static JsonObject object(JsonObject data, String key) {
        JsonElement value = data == null ? null : data.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject data, String key) {
        JsonElement value = data == null ? null : data.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }
}
