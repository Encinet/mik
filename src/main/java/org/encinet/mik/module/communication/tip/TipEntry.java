package org.encinet.mik.module.communication.tip;

import java.util.Objects;
import java.util.Set;

/** One chat-selectable tip with semantic content and matching topics. */
public record TipEntry(String id, Set<String> topics, TipTemplate template) {
    public TipEntry {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Tip id must not be blank");
        topics = Set.copyOf(Objects.requireNonNull(topics, "topics"));
        template = Objects.requireNonNull(template, "template");
        if (topics.isEmpty()) throw new IllegalArgumentException("Tip topics must not be empty");
    }

    public boolean hasTopic(String topic) {
        return topics.contains(topic);
    }
}
