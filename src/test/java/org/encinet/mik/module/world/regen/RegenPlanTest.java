package org.encinet.mik.module.world.regen;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegenPlanTest {

    @Test
    void ordinaryRegenerationHasSeparatePreviewAndApplyStates() {
        RegenBlockFilter filter = RegenBlockFilter.all();
        RegenPlan preview = RegenPlan.regeneratePreview(filter);
        RegenPlan apply = RegenPlan.regenerateApply(filter);

        assertTrue(preview.blocks());
        assertFalse(preview.biomes());
        assertTrue(preview.preview());
        assertNull(preview.upgradeScope());
        assertTrue(apply.blocks());
        assertFalse(apply.biomes());
        assertFalse(apply.preview());
        assertNull(apply.upgradeScope());
    }

    @Test
    void terrainUpgradeHasSeparatePreviewAndApplyStates() {
        RegenPlan preview = RegenPlan.upgradePreview(RegenUpgradeScope.TERRAIN_AND_BIOMES);
        RegenPlan apply = RegenPlan.upgradeApply(
                RegenUpgradeScope.TERRAIN_AND_BIOMES,
                Set.of("minecraft:trial_chambers@4,-2"));

        assertTrue(preview.blocks());
        assertTrue(preview.biomes());
        assertTrue(preview.preview());
        assertTrue(apply.blocks());
        assertTrue(apply.biomes());
        assertFalse(apply.preview());
        assertTrue(apply.structures());
        assertTrue(apply.structureMetadataIdentities().contains("minecraft:trial_chambers@4,-2"));
    }

    @Test
    void biomeOnlyUpgradeCannotPlanBlockWrites() {
        RegenPlan preview = RegenPlan.upgradePreview(RegenUpgradeScope.BIOMES_ONLY);
        assertFalse(preview.blocks());
        assertTrue(preview.biomes());
        assertTrue(preview.preview());
        assertFalse(preview.structures());
    }

    @Test
    void commandOperationKeepsBlockAndUpgradeModesDistinct() {
        RegenOperation blocks = RegenOperation.blocks(RegenBlockFilter.all());
        RegenOperation upgrade = RegenOperation.upgrade(RegenUpgradeScope.TERRAIN_AND_BIOMES);

        assertFalse(blocks.upgrade());
        assertNull(blocks.upgradeScope());
        assertTrue(upgrade.upgrade());
        assertTrue(upgrade.blocks());
        assertTrue(upgrade.blockFilter().replacesAll());
    }
}
