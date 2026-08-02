package org.encinet.mik.module.communication.tip;

import java.util.Objects;
import java.util.Set;

/** One parsed tip with semantic content and optional contextual triggers. */
public record TipEntry(String id, Set<String> topics,
                       Set<TipScene> triggers, TipTemplate template) {
    public TipEntry {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Tip id must not be blank");
        topics = Set.copyOf(Objects.requireNonNull(topics, "topics"));
        triggers = Set.copyOf(Objects.requireNonNull(triggers, "triggers"));
        template = Objects.requireNonNull(template, "template");
        if (topics.isEmpty()) throw new IllegalArgumentException("Tip topics must not be empty");
        if (triggers.contains(TipScene.MANUAL) || triggers.contains(TipScene.PERIODIC)) {
            throw new IllegalArgumentException(
                    "Manual and periodic delivery are implicit tip triggers");
        }
    }

    public boolean supports(TipScene scene) {
        return scene == TipScene.MANUAL
                || scene == TipScene.PERIODIC
                || triggers.contains(scene);
    }

    public boolean hasTopic(String topic) {
        return topic == null || topics.contains(topic);
    }
}
