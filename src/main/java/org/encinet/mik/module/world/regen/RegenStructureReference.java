package org.encinet.mik.module.world.regen;

import java.util.Objects;

/** One structure reference stored by an intersecting chunk. */
record RegenStructureReference(String identity, String type, long packedStartChunk) {

    RegenStructureReference {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(type, "type");
    }
}
