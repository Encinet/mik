package org.encinet.mik.module.communication.tip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads semantic {@code <tip>} records without accepting presentation markup. */
public final class TipCatalog {

    private static final int MAX_TIPS = 512;
    private static final Pattern TIP_BLOCK = Pattern.compile(
            "(?is)<tip\\b([^>]*)>(.*?)</tip\\s*>");
    private static final Pattern TIP_OPEN = Pattern.compile("(?i)<tip\\b");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([a-z][a-z0-9-]*)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern TOPIC = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Pattern LEGACY_PRESENTATION_TAG = Pattern.compile(
            "(?is)<(?:aqua|black|blue|dark_[a-z]+|gold|gray|green|light_purple|red|white|yellow)>"
    );

    private final TipMarkupParser markupParser = new TipMarkupParser();

    public LoadResult load(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public boolean isLegacyDocument(String source) {
        if (source == null || TIP_OPEN.matcher(source).find()) return false;
        return source.contains("===") || LEGACY_PRESENTATION_TAG.matcher(source).find();
    }

    public LoadResult parse(String source) {
        if (source == null) return new LoadResult(List.of(), List.of("Tip document is null"));
        List<TipEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Matcher matcher = TIP_BLOCK.matcher(maskComments(source));
        int cursor = 0;
        while (matcher.find()) {
            reportUnexpected(source, cursor, matcher.start(), errors);
            cursor = matcher.end();
            int line = lineAt(source, matcher.start());
            try {
                Map<String, String> attributes = parseAttributes(matcher.group(1));
                Set<String> topics = parseTopics(attributes.remove("topics"));
                String triggerValue = attributes.remove("triggers");
                String legacySceneValue = attributes.remove("scenes");
                if (triggerValue != null && legacySceneValue != null) {
                    throw new IllegalArgumentException(
                            "Use triggers, not both triggers and the legacy scenes attribute");
                }
                Set<TipScene> triggers = triggerValue != null
                        ? parseTriggers(triggerValue, false)
                        : parseTriggers(legacySceneValue, true);
                if (!attributes.isEmpty()) {
                    throw new IllegalArgumentException("Unsupported <tip> attribute(s): "
                            + String.join(", ", attributes.keySet()));
                }
                String content = matcher.group(2).strip();
                TipTemplate template = markupParser.parse(content);
                String id = hashTip(canonical(topics, triggers, content));
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("Duplicate tip content");
                }
                if (entries.size() >= MAX_TIPS) {
                    throw new IllegalArgumentException("Tip document exceeds " + MAX_TIPS + " entries");
                }
                entries.add(new TipEntry(id, topics, triggers, template));
            } catch (IllegalArgumentException exception) {
                errors.add("line " + line + ": " + exception.getMessage());
            }
        }
        reportUnexpected(source, cursor, source.length(), errors);
        if (entries.isEmpty() && errors.isEmpty()) errors.add("No <tip> records found");
        return new LoadResult(entries, errors);
    }

    private static Map<String, String> parseAttributes(String source) {
        Map<String, String> attributes = new HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(source);
        int cursor = 0;
        while (matcher.find()) {
            if (!source.substring(cursor, matcher.start()).isBlank()) {
                throw new IllegalArgumentException("Malformed <tip> attributes");
            }
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            if (attributes.putIfAbsent(name, matcher.group(2)) != null) {
                throw new IllegalArgumentException("Duplicate <tip> attribute: " + name);
            }
            cursor = matcher.end();
        }
        if (!source.substring(cursor).isBlank()) {
            throw new IllegalArgumentException("Malformed <tip> attributes");
        }
        return attributes;
    }

    private static Set<String> parseTopics(String value) {
        String source = value == null || value.isBlank() ? "general" : value;
        Set<String> result = new LinkedHashSet<>();
        for (String token : source.split("[,\\s]+")) {
            String topic = token.toLowerCase(Locale.ROOT).strip();
            if (!TOPIC.matcher(topic).matches()) {
                throw new IllegalArgumentException("Invalid tip topic: " + token);
            }
            result.add(topic);
        }
        return Set.copyOf(result);
    }

    private static Set<TipScene> parseTriggers(String value, boolean legacyScenes) {
        if (value == null || value.isBlank()) return Set.of();
        Set<TipScene> result = new LinkedHashSet<>();
        for (String token : value.split("[,\\s]+")) {
            TipScene scene = TipScene.fromId(token).orElseThrow(() ->
                    new IllegalArgumentException("Unknown tip trigger: " + token));
            if (scene == TipScene.MANUAL || scene == TipScene.PERIODIC) {
                if (legacyScenes && scene == TipScene.PERIODIC) continue;
                throw new IllegalArgumentException(scene.id()
                        + " is implicit for every tip and cannot be declared as a trigger");
            }
            result.add(scene);
        }
        return Set.copyOf(result);
    }

    private static void reportUnexpected(String source, int start, int end,
                                         List<String> errors) {
        if (start >= end) return;
        String outside = COMMENT.matcher(source.substring(start, end)).replaceAll("");
        if (!outside.isBlank()) {
            errors.add("line " + lineAt(source, start)
                    + ": Content must be inside a <tip> record");
        }
    }

    private static String maskComments(String source) {
        StringBuilder masked = new StringBuilder(source);
        Matcher matcher = COMMENT.matcher(source);
        while (matcher.find()) {
            for (int index = matcher.start(); index < matcher.end(); index++) {
                char character = masked.charAt(index);
                if (character != '\n' && character != '\r') masked.setCharAt(index, ' ');
            }
        }
        return masked.toString();
    }

    private static int lineAt(String source, int offset) {
        int line = 1;
        for (int index = 0; index < Math.min(offset, source.length()); index++) {
            if (source.charAt(index) == '\n') line++;
        }
        return line;
    }

    private static String canonical(Set<String> topics, Set<TipScene> triggers, String content) {
        return topics.stream().sorted().toList() + "\n"
                + triggers.stream().map(TipScene::id).sorted().toList() + "\n" + content;
    }

    private static String hashTip(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(content.hashCode());
        }
    }

    public record LoadResult(List<TipEntry> entries, List<String> errors) {
        public LoadResult {
            entries = List.copyOf(entries);
            errors = List.copyOf(errors);
        }
    }
}
