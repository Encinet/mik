package org.encinet.mik.module.afk;

import java.util.Objects;

/**
 * An accepted server event, not a claim that the sender is human.
 *
 * <p>Block/entity clicks establish observation but do not prove an outcome.
 * Accepted block changes, non-empty inventory actions and positive damage are
 * stronger gameplay evidence. Only the latter may refresh reward eligibility;
 * even these events can be automated and must not be described as human proof.
 * Targets identify the same object across action types to prevent a click and
 * a change on one block from being counted as two different targets.
 *
 * @param type the semantic event category
 * @param targetKey stable world/object/slot identity, without per-event nonces
 */
record AfkActionEvidence(Type type, String targetKey) {

    AfkActionEvidence {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetKey, "targetKey");
    }

    boolean hasAcceptedOutcome() {
        return type.acceptedOutcome;
    }

    enum Type {
        BLOCK_INTERACTION(false),
        BLOCK_CHANGE(true),
        ENTITY_INTERACTION(false),
        INVENTORY(true),
        COMBAT(true);

        private final boolean acceptedOutcome;

        Type(boolean acceptedOutcome) {
            this.acceptedOutcome = acceptedOutcome;
        }
    }
}
