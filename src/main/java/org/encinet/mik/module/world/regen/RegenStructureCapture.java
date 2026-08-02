package org.encinet.mik.module.world.regen;

import java.util.Map;
import java.util.Set;

/** Structure data detached from a generated chunk on the server thread. */
record RegenStructureCapture(
        Set<RegenStructureDescriptor> descriptors,
        Map<String, RegenStructureStartData> starts,
        Set<RegenStructureReference> references
) {

    static final RegenStructureCapture EMPTY = new RegenStructureCapture(Set.of(), Map.of(), Set.of());

    RegenStructureCapture {
        descriptors = Set.copyOf(descriptors);
        starts = Map.copyOf(starts);
        references = Set.copyOf(references);
    }
}
