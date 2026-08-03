package org.encinet.mik.module.space;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceTraversalEntityCoverageTest {

    @Test
    void pollsEveryIndependentNonLivingEntityCategory() {
        assertTrue(requiresPolling(Entity.class));
        assertTrue(requiresPolling(Item.class));
        assertTrue(requiresPolling(ExperienceOrb.class));
        assertTrue(requiresPolling(Arrow.class));
        assertTrue(requiresPolling(EnderPearl.class));
        assertTrue(requiresPolling(Firework.class));
        assertTrue(requiresPolling(FallingBlock.class));
        assertTrue(requiresPolling(TNTPrimed.class));
        assertTrue(requiresPolling(AreaEffectCloud.class));
        assertTrue(requiresPolling(ItemDisplay.class));
        assertTrue(requiresPolling(Interaction.class));
    }

    @Test
    void eventDrivenEntitiesAndComplexPartsAreNotPolledTwice() {
        assertFalse(requiresPolling(Player.class));
        assertFalse(requiresPolling(Zombie.class));
        assertFalse(requiresPolling(Boat.class));
        assertFalse(requiresPolling(ComplexEntityPart.class));
    }

    private static boolean requiresPolling(Class<? extends Entity> type) {
        Entity entity = (Entity) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (_, _, _) -> null);
        return SpaceTraversalController.requiresPolling(entity);
    }
}
