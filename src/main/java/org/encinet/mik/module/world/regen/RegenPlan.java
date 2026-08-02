package org.encinet.mik.module.world.regen;

import java.util.Objects;
import java.util.Set;

/** Internal immutable description of one comparison/application pass. */
record RegenPlan(
        RegenTaskKind kind,
        RegenUpgradeScope upgradeScope,
        RegenBlockFilter blockFilter,
        Set<String> structureMetadataIdentities
) {

    RegenPlan {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(blockFilter, "blockFilter");
        structureMetadataIdentities = Set.copyOf(structureMetadataIdentities);
        if (kind.upgrade() != (upgradeScope != null)) {
            throw new IllegalArgumentException("Upgrade tasks require a scope and regular regeneration must not have one");
        }
        if (kind != RegenTaskKind.UPGRADE_APPLY && !structureMetadataIdentities.isEmpty()) {
            throw new IllegalArgumentException("Only confirmed upgrade application may migrate structure metadata");
        }
        if (upgradeScope == RegenUpgradeScope.BIOMES_ONLY && !structureMetadataIdentities.isEmpty()) {
            throw new IllegalArgumentException("Biome-only upgrades cannot migrate structure metadata");
        }
    }

    static RegenPlan regeneratePreview(RegenBlockFilter filter) {
        return new RegenPlan(RegenTaskKind.REGENERATE_PREVIEW, null, filter, Set.of());
    }

    static RegenPlan regenerateApply(RegenBlockFilter filter) {
        return new RegenPlan(RegenTaskKind.REGENERATE_APPLY, null, filter, Set.of());
    }

    static RegenPlan upgradePreview(RegenUpgradeScope scope) {
        return new RegenPlan(RegenTaskKind.UPGRADE_PREVIEW, scope, RegenBlockFilter.all(), Set.of());
    }

    static RegenPlan upgradeApply(RegenUpgradeScope scope, Set<String> structureMetadataIdentities) {
        return new RegenPlan(RegenTaskKind.UPGRADE_APPLY, scope, RegenBlockFilter.all(),
                structureMetadataIdentities);
    }

    boolean blocks() {
        return !kind.upgrade() || upgradeScope.blocks();
    }

    boolean biomes() {
        return kind.upgrade();
    }

    boolean preview() {
        return kind.preview();
    }

    boolean structures() {
        return kind.upgrade() && blocks();
    }
}
