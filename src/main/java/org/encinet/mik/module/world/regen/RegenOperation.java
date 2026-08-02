package org.encinet.mik.module.world.regen;

import java.util.Objects;

/** Immutable command-level operation saved between preview and application. */
record RegenOperation(RegenBlockFilter blockFilter, RegenUpgradeScope upgradeScope) {

    RegenOperation {
        Objects.requireNonNull(blockFilter, "blockFilter");
        if (upgradeScope != null && !blockFilter.replacesAll()) {
            throw new IllegalArgumentException("World upgrades always use the complete generated block set");
        }
    }

    static RegenOperation blocks(RegenBlockFilter filter) {
        return new RegenOperation(filter, null);
    }

    static RegenOperation upgrade(RegenUpgradeScope scope) {
        return new RegenOperation(RegenBlockFilter.all(), Objects.requireNonNull(scope, "scope"));
    }

    boolean upgrade() {
        return upgradeScope != null;
    }

    boolean blocks() {
        return !upgrade() || upgradeScope.blocks();
    }
}
