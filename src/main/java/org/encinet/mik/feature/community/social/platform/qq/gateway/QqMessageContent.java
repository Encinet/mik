package org.encinet.mik.feature.community.social.platform.qq.gateway;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Bounded, readable projection of the documented QQ group message structures. */
final class QqMessageContent {
    private static final int MAXIMUM_LENGTH = 1_999;
    private static final int MAXIMUM_ELEMENTS = 64;
    private static final int MAXIMUM_DEPTH = 4;

    private final StringBuilder text = new StringBuilder();
    private int elements;
    private boolean truncated;

    private QqMessageContent() { }

    static Projection parse(JsonObject message) {
        String primary = string(message, "content");
        if (primary.isBlank()) primary = string(object(message, "markdown"), "content");
        QqMessageContent projection = new QqMessageContent();
        projection.details(message, 0);
        if (projection.text.isEmpty() && primary.isBlank() && number(message, "message_type") > 0) {
            projection.append("📨", 32);
        }
        if (projection.truncated) projection.text.append('…');
        return new Projection(primary, projection.text.toString());
    }

    private void details(JsonObject message, int depth) {
        if (!visit(depth)) return;
        attachments(array(message, "attachments"));
        JsonObject card = object(message, "ark_data");
        if (card != null) card(card);
        JsonArray nested = array(message, "msg_elements");
        if (nested == null) return;
        long type = number(message, "message_type");
        for (JsonElement element : nested) {
            if (full()) break;
            if (!element.isJsonObject()) { elements++; continue; }
            if (depth >= MAXIMUM_DEPTH) { truncated = true; break; }
            JsonObject child = element.getAsJsonObject();
            String marker = type == 103 || number(child, "message_type") == 103 ? "↪" : "💬";
            JsonObject author = object(child, "author");
            String name = clean(string(author, "username"), 64);
            append(marker + (name.isBlank() ? "" : " " + name + ":"), 80);
            String content = string(child, "content");
            if (content.isBlank()) content = string(object(child, "markdown"), "content");
            append(content, 512);
            details(child, depth + 1);
        }
    }

    private boolean visit(int depth) {
        if (depth > MAXIMUM_DEPTH || full()) { truncated = true; return false; }
        elements++;
        return true;
    }

    private boolean full() {
        if (elements >= MAXIMUM_ELEMENTS || text.length() >= MAXIMUM_LENGTH - 1) {
            truncated = true;
            return true;
        }
        return false;
    }

    private void attachments(JsonArray attachments) {
        if (attachments == null) return;
        for (JsonElement element : attachments) {
            if (full()) break;
            elements++;
            if (!element.isJsonObject()) continue;
            JsonObject attachment = element.getAsJsonObject();
            String type = string(attachment, "content_type").toLowerCase(Locale.ROOT);
            if (type.isBlank() && string(attachment, "filename").isBlank()
                    && string(attachment, "url").isBlank() && string(attachment, "voice_wav_url").isBlank()) continue;
            boolean voice = type.equals("voice") || type.startsWith("audio/");
            String marker = voice ? "🎙️" : type.startsWith("image/") ? "🖼️"
                    : type.startsWith("video/") ? "🎞️" : "📎";
            append(marker + " " + clean(string(attachment, "filename"), 128), 160);
            long width = number(attachment, "width");
            long height = number(attachment, "height");
            long size = number(attachment, "size");
            if (width > 0 && height > 0) append(width + "×" + height, 48);
            if (size > 0) append(size + " B", 48);
            String url = voice ? safeUrl(string(attachment, "voice_wav_url")) : "";
            if (url.isEmpty()) url = safeUrl(string(attachment, "url"));
            link(url);
            if (voice) append(string(attachment, "asr_refer_text"), 512);
        }
    }

    private void card(JsonObject card) {
        if (full()) return;
        elements++;
        String marker = switch (string(card, "ark_type")) {
            case "map" -> "📍";
            case "contact_card" -> "👤";
            case "video_share" -> "🎞️";
            case "music_together" -> "🎵";
            case "picture" -> "🖼️";
            default -> "🔗";
        };
        append(marker, 16);
        JsonObject fields = object(card, "fields");
        Set<String> parts = new LinkedHashSet<>();
        for (String field : new String[]{"title", "desc", "nickname", "address", "source", "tag"}) {
            String value = clean(string(fields, field), field.equals("desc") ? 256 : 128);
            if (!value.isBlank()) parts.add(value);
        }
        JsonElement tags = fields == null ? null : fields.get("tags");
        if (tags != null && tags.isJsonArray()) {
            int count = 0;
            for (JsonElement tag : tags.getAsJsonArray()) {
                if (++count > 4) break;
                if (tag.isJsonPrimitive() && tag.getAsJsonPrimitive().isString()) {
                    String value = clean(tag.getAsString(), 64);
                    if (!value.isBlank()) parts.add(value);
                }
            }
        } else {
            String value = clean(string(fields, "tags"), 128);
            if (!value.isBlank()) parts.add(value);
        }
        String prompt = clean(string(card, "prompt"), 256);
        if (!prompt.isBlank()) parts.add(prompt);
        if (parts.isEmpty()) {
            String name = clean(string(card, "ark_name"), 64);
            if (!name.isBlank()) parts.add(name);
        }
        for (String part : parts) append(part, 256);
        link(safeUrl(string(fields, "jump_url")));
        String preview = safeUrl(string(fields, "preview"));
        if (!preview.isBlank() && !preview.equals(safeUrl(string(fields, "jump_url")))) {
            append("🖼️", 16);
            link(preview);
        }
    }

    private void link(String url) {
        if (url.isBlank()) return;
        // Never publish a partial signed download URL after truncation.
        if (text.length() + url.length() + 1 >= MAXIMUM_LENGTH) { truncated = true; return; }
        append(url, url.length());
    }

    private void append(String value, int limit) {
        String clean = clean(value, limit);
        if (clean.isBlank() || text.length() >= MAXIMUM_LENGTH - 1) return;
        if (!text.isEmpty()) text.append(' ');
        int remaining = MAXIMUM_LENGTH - 1 - text.length();
        if (clean.length() > remaining) {
            text.append(clean(clean, remaining));
            truncated = true;
        } else text.append(clean);
    }

    static String safeUrl(String value) {
        if (value.isBlank() || value.length() > MAXIMUM_LENGTH
                || value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                || Character.isWhitespace(codePoint) || Character.getType(codePoint) == Character.FORMAT)) return "";
        try {
            URI url = URI.create(value.startsWith("//") ? "https:" + value : value);
            if (url.getHost() == null || url.getRawUserInfo() != null || url.getRawAuthority().indexOf('\\') >= 0
                    || !("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))) return "";
            return url.toASCIIString();
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }

    private static String clean(String value, int limit) {
        StringBuilder result = new StringBuilder(Math.min(value.length(), limit));
        boolean whitespace = false;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            offset += Character.charCount(point);
            if (Character.isISOControl(point) || Character.isWhitespace(point)
                    || Character.getType(point) == Character.FORMAT) {
                whitespace = !result.isEmpty();
                continue;
            }
            if (result.length() + Character.charCount(point) + (whitespace ? 1 : 0) > limit) break;
            if (whitespace) result.append(' ');
            whitespace = false;
            result.appendCodePoint(point);
        }
        return result.toString();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object == null ? null : object.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static long number(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return -1;
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException | NumberFormatException invalid) { return -1; }
    }

    private static JsonObject object(JsonObject object, String field) {
        JsonElement value = object == null ? null : object.get(field);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    record Projection(String primaryText, String additionalText) { }
}
