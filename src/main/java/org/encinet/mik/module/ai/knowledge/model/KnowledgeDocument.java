package org.encinet.mik.module.ai.knowledge.model;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Canonical metadata and Markdown body for one active knowledge document. */
public record KnowledgeDocument(
        String id,
        KnowledgeScope scope,
        Optional<UUID> owner,
        String title,
        String language,
        List<String> aliases,
        List<String> tags,
        String kind,
        List<KnowledgeSource> sources,
        boolean protectedDocument,
        long revision,
        Instant createdAt,
        Instant updatedAt,
        Optional<Instant> expiresAt,
        String body
) {
    public KnowledgeDocument {
        id = normalizedId(id);
        scope = Objects.requireNonNull(scope, "scope");
        owner = Objects.requireNonNullElse(owner, Optional.empty());
        if (scope == KnowledgeScope.USER && owner.isEmpty()) {
            throw new IllegalArgumentException("User knowledge requires an owner");
        }
        if (scope == KnowledgeScope.PUBLIC && owner.isPresent()) {
            throw new IllegalArgumentException("Public knowledge cannot have an owner");
        }
        title = requireText(title, "title");
        language = Objects.requireNonNullElse(language, "und").strip()
                .toLowerCase(Locale.ROOT).replace('-', '_');
        if (language.isEmpty()) {
            language = "und";
        }
        aliases = normalizedStrings(aliases);
        tags = normalizedStrings(tags).stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).distinct().toList();
        kind = Objects.requireNonNullElse(kind, "note").strip()
                .toLowerCase(Locale.ROOT);
        if (kind.isEmpty()) {
            kind = "note";
        }
        sources = List.copyOf(Objects.requireNonNullElse(sources, List.of()));
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        expiresAt = Objects.requireNonNullElse(expiresAt, Optional.empty());
        body = Objects.requireNonNullElse(body, "").strip();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Knowledge body must not be blank");
        }
    }

    public boolean expiredAt(Instant instant) {
        return expiresAt.map(value -> !value.isAfter(instant)).orElse(false);
    }

    public String subjectKey() {
        return scope == KnowledgeScope.PUBLIC ? "public:" + id
                : "user:" + owner.orElseThrow() + ':' + id;
    }

    private static String normalizedId(String value) {
        String id = requireText(value, "id").toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("Invalid knowledge document id: " + id);
        }
        return id;
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNullElse(value, "").strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return result;
    }

    private static List<String> normalizedStrings(List<String> values) {
        return Objects.requireNonNullElse(values, List.<String>of()).stream()
                .filter(Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty())
                .distinct().toList();
    }
}
