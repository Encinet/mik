package org.encinet.mik.module.world.regen;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Structure starts and references selected by a confirmed preview manifest. */
record RegenStructureChanges(
        List<RegenStructureStartData> starts,
        List<RegenStructureReference> references
) {

    static final RegenStructureChanges EMPTY = new RegenStructureChanges(List.of(), List.of());

    RegenStructureChanges {
        starts = List.copyOf(starts);
        references = List.copyOf(references);
    }

    static RegenStructureChanges select(RegenStructureCapture capture, Set<String> eligibleIdentities) {
        if (eligibleIdentities.isEmpty()) {
            return EMPTY;
        }
        List<RegenStructureStartData> starts = capture.starts().values().stream()
                .filter(start -> eligibleIdentities.contains(start.identity()))
                .sorted(Comparator.comparing(RegenStructureStartData::identity))
                .toList();
        List<RegenStructureReference> references = capture.references().stream()
                .filter(reference -> eligibleIdentities.contains(reference.identity()))
                .sorted(Comparator.comparing(RegenStructureReference::identity)
                        .thenComparingLong(RegenStructureReference::packedStartChunk))
                .toList();
        return starts.isEmpty() && references.isEmpty()
                ? EMPTY
                : new RegenStructureChanges(starts, references);
    }

    int size() {
        return starts.size() + references.size();
    }
}
