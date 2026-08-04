package org.encinet.mik.module.communication.tip;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Selects the next relevant chat tip in catalog order. */
public final class TipSelector {

    public Optional<TipEntry> select(List<TipEntry> tips, String topic,
                                     Map<String, Long> seenAt, long now,
                                     long repeatCooldownMillis) {
        if (tips == null || topic == null || seenAt == null) {
            throw new NullPointerException();
        }
        if (topic.isBlank()) throw new IllegalArgumentException("Tip topic must not be blank");
        if (repeatCooldownMillis < 0L) {
            throw new IllegalArgumentException("Repeat cooldown must not be negative");
        }
        List<TipEntry> candidates = tips.stream()
                .filter(tip -> tip.hasTopic(topic))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();

        Optional<TipEntry> unseen = candidates.stream()
                .filter(tip -> !seenAt.containsKey(tip.id()))
                .findFirst();
        if (unseen.isPresent()) return unseen;

        return candidates.stream()
                .filter(tip -> now - seenAt.getOrDefault(tip.id(), 0L)
                        >= repeatCooldownMillis)
                .min(Comparator.comparingLong(tip -> seenAt.getOrDefault(tip.id(), 0L)));
    }
}
