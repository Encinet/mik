package org.encinet.mik.module.ai.knowledge.model;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One heading-aware searchable fragment of a Markdown document. */
public record KnowledgeChunk(
        String documentKey,
        String documentId,
        KnowledgeScope scope,
        Optional<UUID> owner,
        int ordinal,
        String title,
        String language,
        List<String> aliases,
        List<String> tags,
        String heading,
        String text,
        URI uri
) {
    public KnowledgeChunk {
        documentKey = Objects.requireNonNull(documentKey, "documentKey");
        documentId = Objects.requireNonNull(documentId, "documentId");
        scope = Objects.requireNonNull(scope, "scope");
        owner = Objects.requireNonNullElse(owner, Optional.empty());
        title = Objects.requireNonNull(title, "title");
        language = Objects.requireNonNullElse(language, "und");
        aliases = List.copyOf(aliases);
        tags = List.copyOf(tags);
        heading = Objects.requireNonNullElse(heading, "").strip();
        text = Objects.requireNonNull(text, "text").strip();
        uri = Objects.requireNonNull(uri, "uri");
    }
}
