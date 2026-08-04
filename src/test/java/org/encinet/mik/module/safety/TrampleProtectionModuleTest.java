package org.encinet.mik.module.safety;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrampleProtectionModuleTest {

    @Test
    void protectsBlocksThatCanBeDestroyedByTrampling() {
        assertTrue(TrampleProtectionModule.isProtected(Material.FARMLAND));
        assertTrue(TrampleProtectionModule.isProtected(Material.TURTLE_EGG));
    }

    @Test
    void preservesNonDestructivePhysicalInteractions() {
        assertFalse(TrampleProtectionModule.isProtected(Material.STONE_PRESSURE_PLATE));
        assertFalse(TrampleProtectionModule.isProtected(Material.BIG_DRIPLEAF));
        assertFalse(TrampleProtectionModule.isProtected(Material.TRIPWIRE));
    }

    @Test
    void doesNotProtectOrdinaryBlocksFromEntityChanges() {
        assertFalse(TrampleProtectionModule.isProtected(Material.DIRT));
        assertFalse(TrampleProtectionModule.isProtected(Material.WHEAT));
    }
}
