package org.encinet.mik.module.communication.tip;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts customized separator/MiniMessage tips while preserving their visible text. */
public final class TipLegacyMigrator {

    private static final String BUNDLED_SEMANTIC_V2_SHA256 =
            "af2064281f707849f8a238107bc83d315285a1ae85ba643ead68535f9d074d62";

    private static final Pattern PRESENTATION_TAG = Pattern.compile(
            "(?is)</?(?:aqua|black|blue|dark_[a-z]+|gold|gray|green|light_purple|red|white|yellow|"
                    + "bold|italic|underlined|strikethrough|obfuscated)>"
    );
    private static final Pattern SEMANTIC_VALUE = Pattern.compile(
            "(?i)/(?:spawn|afk|announcements|sethome|home|back|reback|lang|music|pvp|trash|"
                    + "tpsbar|menu|performance|flyspeed|fly|msg|reply|r|public|cancel|c|"
                    + "nametag|hat|tip|weblogin)(?![a-z0-9_])|(?:[a-z0-9-]+\\.)+[a-z]{2,}"
    );
    private static final Map<String, String> COMMAND_TOPICS = Map.ofEntries(
            Map.entry("spawn", "spawn"),
            Map.entry("afk", "afk"),
            Map.entry("announcements", "announcements"),
            Map.entry("sethome", "home"),
            Map.entry("home", "home"),
            Map.entry("back", "teleport"),
            Map.entry("reback", "teleport"),
            Map.entry("lang", "language"),
            Map.entry("music", "music"),
            Map.entry("pvp", "pvp"),
            Map.entry("trash", "trash"),
            Map.entry("tpsbar", "performance"),
            Map.entry("performance", "performance"),
            Map.entry("menu", "menu"),
            Map.entry("fly", "flight"),
            Map.entry("flyspeed", "flight"),
            Map.entry("msg", "private-chat"),
            Map.entry("reply", "private-chat"),
            Map.entry("r", "private-chat"),
            Map.entry("public", "private-chat"),
            Map.entry("cancel", "chat-settings"),
            Map.entry("c", "chat-settings"),
            Map.entry("nametag", "nametag"),
            Map.entry("hat", "hat"),
            Map.entry("tip", "tips"),
            Map.entry("weblogin", "website"));
    private static final List<String> BUNDLED_V1 = List.of(
            "使用 /spawn 可以随时回到主城",
            "挂机时可以使用 /afk 稍后回来 设置头顶状态；上线后可以用 /announcements 查看最近公告",
            "成员可以使用 /sethome 设置家，之后用 /home 回去",
            "遇到问题时，先查看官网 mcmik.top，那里通常有最新说明");

    public boolean isBundledV1(String source) {
        return visibleBlocks(source).stream().map(TipLegacyMigrator::normalizeWhitespace).toList()
                .equals(BUNDLED_V1);
    }

    /** Recognizes the short semantic default shipped before contextual triggers became additive. */
    public boolean isBundledSemanticV2(String source) {
        if (source == null) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    source.getBytes(StandardCharsets.UTF_8)))
                    .equals(BUNDLED_SEMANTIC_V2_SHA256);
        } catch (NoSuchAlgorithmException exception) {
            return false;
        }
    }

    public String migrate(String source) {
        List<String> blocks = visibleBlocks(source);
        if (blocks.isEmpty()) throw new IllegalArgumentException("Legacy tip file has no visible tips");
        StringBuilder result = new StringBuilder("""
                <!-- Automatically migrated from the legacy separator format. -->

                """);
        for (String block : blocks) {
            Set<String> topics = topics(block);
            result.append("<tip topics=\"")
                    .append(String.join(" ", topics))
                    .append("\"");
            if (!topics.equals(Set.of("general"))) result.append(" triggers=\"chat\"");
            result.append(">\n")
                    .append(semanticMarkup(block)).append("\n</tip>\n\n");
        }
        return result.toString();
    }

    private static List<String> visibleBlocks(String source) {
        if (source == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String raw : source.split("===")) {
            String visible = PRESENTATION_TAG.matcher(raw).replaceAll("").strip();
            if (!visible.isBlank()) result.add(visible);
        }
        return List.copyOf(result);
    }

    private static Set<String> topics(String content) {
        Set<String> topics = new LinkedHashSet<>();
        Matcher matcher = SEMANTIC_VALUE.matcher(content);
        while (matcher.find()) {
            String value = matcher.group().toLowerCase(Locale.ROOT);
            if (value.startsWith("/")) {
                String topic = COMMAND_TOPICS.get(value.substring(1));
                if (topic != null) topics.add(topic);
            } else {
                topics.add("website");
            }
        }
        if (topics.isEmpty()) topics.add("general");
        return Set.copyOf(topics);
    }

    private static String semanticMarkup(String content) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = SEMANTIC_VALUE.matcher(content);
        int cursor = 0;
        while (matcher.find()) {
            result.append(escape(content.substring(cursor, matcher.start())));
            String value = matcher.group();
            result.append(value.startsWith("/") ? "<command>" : "<site>")
                    .append(escape(value))
                    .append(value.startsWith("/") ? "</command>" : "</site>");
            cursor = matcher.end();
        }
        return result.append(escape(content.substring(cursor))).toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }
}
