package org.encinet.mik.feature.server.prison;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrisonRestrictionsTest {
    private final UUID account = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final Player visitor = mock(Player.class);
    private boolean banned = true;
    private final PrisonRestrictions rules = new PrisonRestrictions(id -> banned && account.equals(id),
            mock(LanguageService.class));
    private GameMode mode = GameMode.CREATIVE;
    private boolean flying = true;
    private boolean allowFlight = true;
    private boolean collidable = true;

    PrisonRestrictionsTest() {
        when(player.getUniqueId()).thenReturn(account);
        when(visitor.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getGameMode()).thenAnswer(_ -> mode);
        when(player.isFlying()).thenAnswer(_ -> flying);
        when(player.getAllowFlight()).thenAnswer(_ -> allowFlight);
        when(player.isCollidable()).thenAnswer(_ -> collidable);
        doAnswer(call -> {
            var event = new PlayerGameModeChangeEvent(player, call.getArgument(0),
                    PlayerGameModeChangeEvent.Cause.PLUGIN, null);
            rules.onGameMode(event);
            if (!event.isCancelled()) mode = event.getNewGameMode();
            return null;
        }).when(player).setGameMode(any());
        doAnswer(call -> { flying = call.getArgument(0); return null; }).when(player).setFlying(anyBoolean());
        doAnswer(call -> { allowFlight = call.getArgument(0); return null; }).when(player).setAllowFlight(anyBoolean());
        doAnswer(call -> { collidable = call.getArgument(0); return null; }).when(player).setCollidable(anyBoolean());
    }

    @Test
    void keepsAdventureModeAndDisablesFlightUntilTheBanIsLifted() {
        when(player.isGliding()).thenReturn(true);
        when(player.isRiptiding()).thenReturn(true);
        when(player.hasActiveItem()).thenReturn(true);
        assertTrue(rules.enforce(player));
        assertEquals(GameMode.ADVENTURE, mode);
        assertFalse(flying);
        assertFalse(allowFlight);
        assertFalse(collidable);
        verify(player).setGliding(false);
        verify(player).setRiptiding(false);
        verify(player).clearActiveItem();
        verify(player).closeInventory();
        verify(player).closeDialog();

        player.setGameMode(GameMode.SPECTATOR);
        assertEquals(GameMode.ADVENTURE, mode);
        var flight = new PlayerToggleFlightEvent(player, true);
        rules.onFlight(flight);
        assertTrue(flight.isCancelled());
        var glide = new EntityToggleGlideEvent(player, true);
        rules.onGlide(glide);
        assertTrue(glide.isCancelled());

        allowFlight = true;
        rules.enforce(player);
        assertFalse(allowFlight);

        banned = false;
        assertFalse(rules.enforce(player));
        assertEquals(GameMode.CREATIVE, mode);
        assertTrue(allowFlight);
        assertTrue(collidable);
        assertFalse(flying);
        verify(player, times(2)).updateCommands();
    }

    @Test
    void restoresModeAtQuitDespiteAnActiveBanAndAppliesAdventureOnTheNextSession() {
        rules.enforce(player);
        rules.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(GameMode.CREATIVE, mode);
        assertTrue(allowFlight);
        assertTrue(rules.enforce(player));
        assertEquals(GameMode.ADVENTURE, mode);
        assertFalse(allowFlight);
        rules.close();
        assertEquals(GameMode.CREATIVE, mode);
    }

    @Test
    void blocksEveryCommandAndHidesCommandSuggestionsWhileTheBanIsActive() {
        for (String command : List.of("/msg Bob hi", "/r hi", "/help", "/rules", "/minecraft:help", "/fly")) {
            var event = new PlayerCommandPreprocessEvent(player, command, java.util.Set.of());
            rules.onCommand(event);
            assertTrue(event.isCancelled(), command);
        }
        var labels = new ArrayList<>(List.of("help", "prison"));
        rules.onCommandSend(new PlayerCommandSendEvent(player, labels));
        assertTrue(labels.isEmpty());

        var ordinary = new PlayerCommandPreprocessEvent(visitor, "/fly", java.util.Set.of());
        rules.onCommand(ordinary);
        assertFalse(ordinary.isCancelled());
        banned = false;
        var pardoned = new PlayerCommandPreprocessEvent(player, "/fly", java.util.Set.of());
        rules.onCommand(pardoned);
        assertFalse(pardoned.isCancelled());
    }

    @Test
    void deniesServerSideInventoryActionsInteractionMountingAndDamageAttribution() {
        var interact = new PlayerInteractEvent(player, Action.PHYSICAL, null, null, BlockFace.UP);
        rules.onInteract(interact);
        assertEquals(Event.Result.DENY, interact.useInteractedBlock());
        assertEquals(Event.Result.DENY, interact.useItemInHand());

        var inventory = mock(InventoryClickEvent.class);
        when(inventory.getWhoClicked()).thenReturn(player);
        rules.onInventoryClick(inventory);
        verify(inventory).setCancelled(true);

        var pickup = mock(EntityPickupItemEvent.class);
        when(pickup.getEntity()).thenReturn(player);
        rules.onPickup(pickup);
        verify(pickup).setCancelled(true);

        Entity vehicle = mock(Entity.class);
        var mount = new EntityMountEvent(player, vehicle);
        rules.onMount(mount);
        assertTrue(mount.isCancelled());
        var visitorMount = new EntityMountEvent(visitor, vehicle);
        rules.onMount(visitorMount);
        assertFalse(visitorMount.isCancelled());

        var damage = mock(EntityDamageEvent.class);
        var source = mock(DamageSource.class);
        when(damage.getEntity()).thenReturn(visitor);
        when(damage.getDamageSource()).thenReturn(source);
        when(source.getCausingEntity()).thenReturn(player);
        rules.onDamage(damage);
        verify(damage).setCancelled(true);
    }
}
