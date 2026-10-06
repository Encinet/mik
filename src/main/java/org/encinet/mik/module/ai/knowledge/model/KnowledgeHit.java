package org.encinet.mik.module.ai.knowledge.model;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** A ranked public knowledge result returned to an AI tool. */
public record KnowledgeHit(
        String documentId,
        String title,
        String language,
        List<String> tags,
        String heading,
        String excerpt,
        URI uri,
        double score
) {
    public KnowledgeHit {
        documentId = Objects.requireNonNull(documentId, "documentId");
        title = Objects.requireNonNull(title, "title");
        language = Objects.requireNonNullElse(language, "und");
        tags = List.copyOf(tags);
        heading = Objects.requireNonNullElse(heading, "");
        excerpt = Objects.requireNonNull(excerpt, "excerpt");
        uri = Objects.requireNonNull(uri, "uri");
    }
}
