package org.encinet.mik.module.ai.knowledge.adapter.markdown;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Parses and renders the constrained YAML front matter used by knowledge Markdown. */
public final class MarkdownKnowledgeCodec {
    private static final String DELIMITER = "---";

    public KnowledgeDocument parse(
            Path relativePath,
            KnowledgeScope expectedScope,
            Optional<UUID> expectedOwner,
            String source,
            Instant fileModifiedAt
    ) {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(expectedScope, "expectedScope");
        Objects.requireNonNull(expectedOwner, "expectedOwner");
        String normalized = Objects.requireNonNullElse(source, "")
                .replace("\u0000", "");
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        FrontMatter frontMatter = frontMatter(normalized);
        YamlConfiguration yaml = new YamlConfiguration();
        if (!frontMatter.yaml().isBlank()) {
            try {
                yaml.loadFromString(frontMatter.yaml());
            } catch (Exception error) {
                throw new IllegalArgumentException(
                        "Invalid knowledge front matter in " + relativePath, error);
            }
        }

        validateBoundary(yaml, expectedScope, expectedOwner, relativePath);
        String body = frontMatter.body().strip();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Knowledge Markdown is empty: " + relativePath);
        }
        Instant fallback = Objects.requireNonNullElse(fileModifiedAt, Instant.now());
        String id = yaml.getString("id", "").strip();
        if (id.isEmpty()) {
            id = derivedId(relativePath);
        }
        String title = yaml.getString("title", "").strip();
        if (title.isEmpty()) {
            title = firstTitle(body).orElseGet(() -> fileTitle(relativePath));
        }
        return new KnowledgeDocument(
                id,
                expectedScope,
                expectedOwner,
                title,
                yaml.getString("language", "und"),
                strings(yaml, "aliases"),
                strings(yaml, "tags"),
                yaml.getString("kind", "note"),
                sources(yaml),
                yaml.getBoolean("protected", false),
                Math.max(1L, yaml.getLong("revision", 1L)),
                instant(yaml.get("created-at"), fallback),
                instant(yaml.get("updated-at"), fallback),
                optionalInstant(yaml.get("expires-at")),
                body);
    }

    public String render(KnowledgeDocument document) {
        Objects.requireNonNull(document, "document");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.set("id", document.id());
        yaml.set("scope", document.scope().name().toLowerCase(Locale.ROOT));
        document.owner().ifPresent(owner -> yaml.set("owner", owner.toString()));
        yaml.set("title", document.title());
        yaml.set("language", document.language());
        if (!document.aliases().isEmpty()) {
            yaml.set("aliases", document.aliases());
        }
        if (!document.tags().isEmpty()) {
            yaml.set("tags", document.tags());
        }
        yaml.set("kind", document.kind());
        if (!document.sources().isEmpty()) {
            List<Map<String, String>> sources = new ArrayList<>();
            for (KnowledgeSource source : document.sources()) {
                Map<String, String> entry = new LinkedHashMap<>();
                if (!source.title().isBlank()) {
                    entry.put("title", source.title());
                }
                entry.put("url", source.url().toASCIIString());
                sources.add(entry);
            }
            yaml.set("sources", sources);
        }
        yaml.set("protected", document.protectedDocument());
        yaml.set("revision", document.revision());
        yaml.set("created-at", document.createdAt().toString());
        yaml.set("updated-at", document.updatedAt().toString());
        document.expiresAt().ifPresent(value -> yaml.set("expires-at", value.toString()));
        String serialized = yaml.saveToString().stripTrailing();
        return DELIMITER + '\n' + serialized + '\n' + DELIMITER + "\n\n"
                + document.body().strip() + '\n';
    }

    private static void validateBoundary(
            YamlConfiguration yaml,
            KnowledgeScope scope,
            Optional<UUID> owner,
            Path path
    ) {
        String declaredScope = yaml.getString("scope", "").strip();
        if (!declaredScope.isEmpty()
                && !declaredScope.equalsIgnoreCase(scope.name())) {
            throw new IllegalArgumentException("Knowledge scope conflicts with path: " + path);
        }
        String declaredOwner = yaml.getString("owner", "").strip();
        if (scope == KnowledgeScope.PUBLIC && !declaredOwner.isEmpty()) {
            throw new IllegalArgumentException("Public knowledge declares an owner: " + path);
        }
        if (scope == KnowledgeScope.USER && !declaredOwner.isEmpty()) {
            UUID parsed;
            try {
                parsed = UUID.fromString(declaredOwner);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Invalid knowledge owner in " + path, error);
            }
            if (!owner.orElseThrow().equals(parsed)) {
                throw new IllegalArgumentException("Knowledge owner conflicts with path: " + path);
            }
        }
    }

    private static FrontMatter frontMatter(String source) {
        List<String> lines = source.lines().toList();
        if (lines.isEmpty() || !DELIMITER.equals(lines.getFirst().strip())) {
            return new FrontMatter("", source);
        }
        int cursor = firstLineEnd(source);
        int yamlStart = cursor;
        while (cursor < source.length()) {
            int end = lineEnd(source, cursor);
            String line = source.substring(cursor, end).strip();
            if (DELIMITER.equals(line) || "...".equals(line)) {
                int bodyStart = end;
                if (bodyStart < source.length() && source.charAt(bodyStart) == '\r') {
                    bodyStart++;
                }
                if (bodyStart < source.length() && source.charAt(bodyStart) == '\n') {
                    bodyStart++;
                }
                return new FrontMatter(source.substring(yamlStart, cursor),
                        source.substring(bodyStart));
            }
            cursor = end;
            if (cursor < source.length() && source.charAt(cursor) == '\r') {
                cursor++;
            }
            if (cursor < source.length() && source.charAt(cursor) == '\n') {
                cursor++;
            }
        }
        throw new IllegalArgumentException("Unclosed knowledge front matter");
    }

    private static int firstLineEnd(String source) {
        int end = source.indexOf('\n');
        return end < 0 ? source.length() : end + 1;
    }

    private static int lineEnd(String source, int start) {
        int end = source.indexOf('\n', start);
        if (end < 0) {
            end = source.length();
        }
        return end > start && source.charAt(end - 1) == '\r' ? end - 1 : end;
    }

    private static List<String> strings(YamlConfiguration yaml, String path) {
        if (yaml.isList(path)) {
            return yaml.getList(path, List.of()).stream().filter(Objects::nonNull)
                    .map(String::valueOf).map(String::strip)
                    .filter(value -> !value.isEmpty()).toList();
        }
        String scalar = yaml.getString(path, "").strip();
        return scalar.isEmpty() ? List.of() : List.of(scalar);
    }

    private static List<KnowledgeSource> sources(YamlConfiguration yaml) {
        List<KnowledgeSource> result = new ArrayList<>();
        for (Map<?, ?> entry : yaml.getMapList("sources")) {
            String url = Objects.toString(entry.get("url"), "").strip();
            if (url.isEmpty()) {
                continue;
            }
            result.add(new KnowledgeSource(
                    Objects.toString(entry.get("title"), ""), URI.create(url)));
        }
        for (String scalar : yaml.getStringList("source-urls")) {
            if (!scalar.isBlank()) {
                result.add(new KnowledgeSource("", URI.create(scalar.strip())));
            }
        }
        return List.copyOf(result);
    }

    private static Instant instant(Object value, Instant fallback) {
        return optionalInstant(value).orElse(fallback);
    }

    private static Optional<Instant> optionalInstant(Object value) {
        if (value instanceof java.util.Date date) {
            return Optional.of(date.toInstant());
        }
        if (value instanceof Instant instant) {
            return Optional.of(instant);
        }
        String text = Objects.toString(value, "").strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(text));
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Invalid knowledge timestamp: " + text, error);
        }
    }

    private static Optional<String> firstTitle(String body) {
        boolean fenced = false;
        for (String line : body.lines().toList()) {
            String stripped = line.strip();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                fenced = !fenced;
                continue;
            }
            if (!fenced && stripped.matches("^#{1,6}\\s+.+$")) {
                return Optional.of(stripped.replaceFirst("^#{1,6}\\s+", "").strip());
            }
        }
        return Optional.empty();
    }

    private static String fileTitle(Path path) {
        String name = path.getFileName().toString();
        int extension = name.toLowerCase(Locale.ROOT).lastIndexOf(".md");
        if (extension > 0) {
            name = name.substring(0, extension);
        }
        String title = name.replace('_', ' ').replace('-', ' ').strip();
        return title.isEmpty() ? "Untitled" : title;
    }

    private static String derivedId(Path path) {
        String candidate = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT)
                .replaceFirst("(?i)\\.md$", "").replace('/', '.')
                .replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (candidate.isEmpty()) {
            candidate = "document";
        }
        if (candidate.length() <= 96) {
            return candidate;
        }
        return candidate.substring(0, 79) + '-' + digest(candidate).substring(0, 16);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record FrontMatter(String yaml, String body) {
    }
}
