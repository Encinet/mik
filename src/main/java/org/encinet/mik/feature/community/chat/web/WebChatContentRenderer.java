package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Projects shared semantic content to safe text, style and link runs, without game-only data. */
final class WebChatContentRenderer {
    // Leave room for the message envelope inside the 128 KiB browser frame limit.
    private static final int MAX_CONTENT_BYTES = 120 * 1024;
    private static final int MAX_PARTS = 256;
    private static final int MAX_HREF_LENGTH = 2048;
    private static final int MAX_TOTAL_HREF_LENGTH = 4096;

    private WebChatContentRenderer() { }

    static JsonArray render(ChatContent content) {
        JsonArray parts = new JsonArray();
        StringBuilder plain = new StringBuilder();
        int hrefLength = 0;
        // Every UTF-16 unit needs at most six bytes in Gson JSON, including HTML escaping.
        int presentationBudget = MAX_CONTENT_BYTES - content.plainText().length() * 6 - 16;
        for (ChatNode node : content.nodes()) {
            String href = node instanceof ChatNode.Link link ? href(link.target()) : null;
            if (href != null && (href.length() > MAX_HREF_LENGTH
                    || hrefLength + href.length() > MAX_TOTAL_HREF_LENGTH)) href = null;
            boolean mention = node instanceof ChatNode.PlayerMention || node instanceof ChatNode.BroadcastMention;
            JsonObject style = style(node.style());
            // Reserve JSON structure, a preceding plain run and worst-case HTML escaping of URLs.
            int bytes = 64 + style.toString().length() + (href == null ? 0 : href.length() * 6);
            if ((href != null || mention || !style.isEmpty())
                    && parts.size() + (plain.isEmpty() ? 0 : 1) < MAX_PARTS - 1
                    && bytes <= presentationBudget) {
                flush(parts, plain);
                JsonObject part = new JsonObject();
                part.addProperty("text", node.visibleText());
                if (href != null) part.addProperty("href", href);
                if (mention) part.addProperty("mention", true);
                if (!style.isEmpty()) part.add("style", style);
                parts.add(part);
                if (href != null) hrefLength += href.length();
                presentationBudget -= bytes;
            } else {
                // Retain every visible character when presentation exceeds the transport budget.
                plain.append(node.visibleText());
            }
        }
        flush(parts, plain);
        return parts;
    }

    private static JsonObject style(ChatStyle style) {
        JsonObject result = new JsonObject();
        if (style.color() != null) result.addProperty("color", style.color());
        if (style.gradientEndColor() != null) result.addProperty("gradientEndColor", style.gradientEndColor());
        if (style.bold()) result.addProperty("bold", true);
        if (style.italic()) result.addProperty("italic", true);
        if (style.underlined()) result.addProperty("underlined", true);
        if (style.strikethrough()) result.addProperty("strikethrough", true);
        return result;
    }

    private static void flush(JsonArray parts, StringBuilder plain) {
        if (plain.isEmpty()) return;
        JsonObject part = new JsonObject();
        part.addProperty("text", plain.toString());
        parts.add(part);
        plain.setLength(0);
    }

    private static String href(URI target) {
        if ("mailto".equalsIgnoreCase(target.getScheme())) {
            // Encode a single model-validated address, including local-part '?' and '#'.
            return "mailto:" + URLEncoder.encode(target.getSchemeSpecificPart(), StandardCharsets.UTF_8)
                    .replace("+", "%20").replace("%40", "@");
        }
        try {
            if (target.getHost() == null) {
                String authority = target.getRawAuthority();
                int separator = authority.lastIndexOf(':');
                String host = separator < 0 ? authority : authority.substring(0, separator);
                String port = separator < 0 ? "" : authority.substring(separator);
                String asciiAuthority = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) + port;
                String suffix = target.toString().substring(target.getScheme().length() + 3 + authority.length());
                target = URI.create(target.getScheme() + "://" + asciiAuthority + suffix);
            }
            target = target.parseServerAuthority();
            if (target.getHost() == null || target.getPort() > 65535) return null;
            return target.toASCIIString();
        } catch (IllegalArgumentException | URISyntaxException invalid) {
            return null;
        }
    }
}
