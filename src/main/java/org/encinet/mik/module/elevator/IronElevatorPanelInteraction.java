package org.encinet.mik.module.elevator;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.function.BiPredicate;

final class IronElevatorPanelInteraction implements Listener {

    private final BiPredicate<Player, Boolean> click;

    IronElevatorPanelInteraction(BiPredicate<Player, Boolean> click) {
        this.click = click;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR) return;
        boolean activate = event.getHand() == EquipmentSlot.HAND
                && event.useItemInHand() != Event.Result.DENY
                && (event.useInteractedBlock() != Event.Result.DENY
                    || action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR);
        if (!click.test(event.getPlayer(), activate)) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(BlockDamageEvent event) {
        if (click.test(event.getPlayer(), false)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (click.test(event.getPlayer(), false)) event.setCancelled(true);
    }
}
