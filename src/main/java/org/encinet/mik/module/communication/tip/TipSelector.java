package org.encinet.mik.module.communication.tip;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Selects a relevant unseen or sufficiently old tip for one trigger. */
public final class TipSelector {

    public Optional<TipEntry> select(List<TipEntry> tips, TipScene scene, String topic,
                                     Map<String, Long> seenAt, long now,
                                     RandomGenerator random) {
        if (tips == null || scene == null || seenAt == null || random == null) {
            throw new NullPointerException();
        }
        List<TipEntry> candidates = tips.stream()
                .filter(tip -> tip.supports(scene))
                .filter(tip -> tip.hasTopic(topic))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();

        List<TipEntry> unseen = candidates.stream()
                .filter(tip -> !seenAt.containsKey(tip.id()))
                .toList();
        if (!unseen.isEmpty()) {
            return Optional.of(unseen.get(random.nextInt(unseen.size())));
        }
        if (scene == TipScene.MANUAL) {
            return candidates.stream().min(Comparator.comparingLong(
                    tip -> seenAt.getOrDefault(tip.id(), 0L)));
        }
        return candidates.stream()
                .filter(tip -> now - seenAt.getOrDefault(tip.id(), 0L)
                        >= scene.repeatCooldownMillis())
                .min(Comparator.comparingLong(tip -> seenAt.getOrDefault(tip.id(), 0L)));
    }
}
