package org.encinet.mik.module.elevator;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class IronElevatorPanelInteractionTest {

    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, arguments) -> {
                throw new UnsupportedOperationException(method.getName());
            });

    @Test
    void consumesLeftAndRightClicksBeforeTheyCanMineOrUseTheWall() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        for (Action action : List.of(Action.LEFT_CLICK_BLOCK, Action.RIGHT_CLICK_BLOCK,
                Action.LEFT_CLICK_AIR, Action.RIGHT_CLICK_AIR)) {
            List<Boolean> activation = new ArrayList<>();
            IronElevatorPanelInteraction input = new IronElevatorPanelInteraction((viewer, activate) -> {
                activation.add(activate);
                return true;
            });
            PlayerInteractEvent event = new PlayerInteractEvent(player, action, null,
                    action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR ? null : world.block(0, 21, 1),
                    BlockFace.NORTH, EquipmentSlot.HAND);
            input.onInteract(event);
            assertEquals(List.of(true), activation);
            assertEquals(Event.Result.DENY, event.useInteractedBlock());
            assertEquals(Event.Result.DENY, event.useItemInHand());
        }
    }

    @Test
    void cancelsDamageAndCreativeBreakWithoutSelectingAnotherFloor() {
        List<Boolean> activation = new ArrayList<>();
        IronElevatorPanelInteraction input = new IronElevatorPanelInteraction((viewer, activate) -> {
            activation.add(activate);
            return true;
        });
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        BlockDamageEvent damage = new BlockDamageEvent(player, world.block(0, 21, 1), BlockFace.NORTH, null, true);
        BlockBreakEvent breaking = new BlockBreakEvent(world.block(0, 21, 1), player);
        input.onDamage(damage);
        input.onBreak(breaking);
        assertTrue(damage.isCancelled());
        assertTrue(breaking.isCancelled());
        assertEquals(List.of(false, false), activation);
    }

    @Test
    void protectsDisabledAndOffhandTargetsAndRespectsOtherPluginsDenials() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        List<Boolean> activation = new ArrayList<>();
        IronElevatorPanelInteraction input = new IronElevatorPanelInteraction((viewer, activate) -> {
            activation.add(activate);
            return true;
        });
        PlayerInteractEvent offhand = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null,
                world.block(0, 21, 1), BlockFace.NORTH, EquipmentSlot.OFF_HAND);
        PlayerInteractEvent denied = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null,
                world.block(0, 21, 1), BlockFace.NORTH, EquipmentSlot.HAND);
        denied.setCancelled(true);
        input.onInteract(offhand);
        input.onInteract(denied);
        assertEquals(List.of(false, false), activation);
        assertEquals(Event.Result.DENY, offhand.useItemInHand());
    }

    @Test
    void doesNotProtectUnrelatedBlocksOrPressurePlates() {
        IronElevatorTestWorld world = new IronElevatorTestWorld();
        IronElevatorPanelInteraction input = new IronElevatorPanelInteraction((viewer, activate) -> false);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null,
                world.block(0, 21, 1), BlockFace.NORTH, EquipmentSlot.HAND);
        input.onInteract(event);
        assertEquals(Event.Result.ALLOW, event.useInteractedBlock());
        BlockBreakEvent breaking = new BlockBreakEvent(world.block(0, 21, 1), player);
        input.onBreak(breaking);
        assertFalse(breaking.isCancelled());
        IronElevatorPanelInteraction never = new IronElevatorPanelInteraction((viewer, activate) -> {
            fail("Physical actions must not select floors");
            return true;
        });
        never.onInteract(new PlayerInteractEvent(player, Action.PHYSICAL, null,
                world.block(0, 21, 1), BlockFace.UP, null));
    }
}
