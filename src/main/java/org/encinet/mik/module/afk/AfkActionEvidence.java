package org.encinet.mik.module.afk;

import java.util.Objects;

record AfkActionEvidence(Type type, String targetKey) {

    AfkActionEvidence {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetKey, "targetKey");
    }

    enum Type {
        BLOCK_INTERACTION,
        BLOCK_CHANGE,
        ENTITY_INTERACTION,
        INVENTORY,
        COMBAT
    }
}
