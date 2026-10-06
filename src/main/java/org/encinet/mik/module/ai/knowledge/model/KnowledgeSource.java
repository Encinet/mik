package org.encinet.mik.module.ai.knowledge.model;

import java.net.URI;
import java.util.Objects;

/** An optional external source retained as provenance for learned knowledge. */
public record KnowledgeSource(String title, URI url) {
    public KnowledgeSource {
        title = Objects.requireNonNullElse(title, "").strip();
        url = Objects.requireNonNull(url, "url").normalize();
        String scheme = Objects.requireNonNullElse(url.getScheme(), "");
        if (!url.isAbsolute() || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Knowledge source must be an HTTP(S) URI");
        }
        if (url.getUserInfo() != null) {
            throw new IllegalArgumentException("Knowledge source URI must not contain credentials");
        }
    }
}
