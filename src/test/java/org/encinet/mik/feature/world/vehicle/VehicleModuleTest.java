package org.encinet.mik.feature.world.vehicle;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuHandle;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.runtime.FloatingMenuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleModuleTest {
    @TempDir Path directory;
    private final World world = mock(World.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final Server server = mock(Server.class);
    private final FloatingMenuService menus = mock(FloatingMenuService.class);
    private VehicleStore store;
    private VehicleModule module;
    private VehicleInstance instance;

    @BeforeEach void enable() throws Exception {
        var plugin = mock(JavaPlugin.class);
        var language = mock(LanguageService.class);
        var playerId = UUID.randomUUID();
        var worldId = UUID.randomUUID();
        when(plugin.namespace()).thenReturn("mik");
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        when(server.getWorld(worldId)).thenReturn(world);
        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("mik.vehicle.use")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getInventory()).thenReturn(inventory);
        when(language.t(eq(player), eq(Message.VEHICLE_MENU_BLOCKED), anyString())).thenAnswer(call -> call.getArgument(2));

        var saved = new VehicleInstance(UUID.randomUUID(), worldId, playerId,
                VehicleFixtures.body(VehicleDefinition.Kind.CAR, new VehicleVector(0, 64, 0)));
        store = new VehicleStore(directory.resolve("vehicles"));
        store.saveDefinition(saved.body.definition);
        store.saveInstances(List.of(saved.snapshot()));
        module = new VehicleModule(plugin, language);
        module.enable();
        instance = module.menuTarget(player, saved.id);
        FloatingMenus.install(menus);
    }

    @AfterEach void disable() {
        try { if (module != null) module.disable(); }
        finally { FloatingMenus.uninstall(); }
    }

    @Test void boardingUsesTheSameReasonsForUnavailableSeatsAsTheMenu() {
        instance.entities = mock(VehicleEntityGroup.class);
        when(instance.entities.firstAvailable(true)).thenReturn(-1);
        assertCannotBoard(true, VehicleModule.Block.DRIVER_OCCUPIED);
        assertCannotBoard(false, VehicleModule.Block.NO_PASSENGER_SEAT);
        when(player.getLocation()).thenReturn(new Location(world, 33, 64, 0));
        assertCannotBoard(true, VehicleModule.Block.TOO_FAR);
        assertNull(module.mountedVehicle(player));
        verify(server.getPluginManager(), never()).callEvent(any());
    }

    @Test void maintenanceRevalidatesStateAndConsumesMaterialOnlyAfterSaving() throws Exception {
        var item = mock(ItemStack.class);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getType()).thenReturn(Material.COAL);
        when(item.getAmount()).thenReturn(2);
        instance.body.health = 65;
        assertCannotService(false, VehicleModule.Block.NEED_IRON);
        instance.body.fuel = 50;
        assertNull(module.serviceBlock(player, instance, true));
        instance.body.engineRunning = true;
        assertCannotService(true, VehicleModule.Block.STOP_ENGINE);
        assertEquals(50, instance.body.fuel);
        verify(item, never()).setAmount(anyInt());
        instance.body.engineRunning = false;
        module.service(player, instance.id, true);
        assertEquals(60, store.loadInstances().getFirst().fuel());
        verify(item).setAmount(1);
    }

    @Test void boardingAndHudDoNotOpenAMenuAndDrivingShortcutsYieldToIt() {
        var entities = mock(VehicleEntityGroup.class);
        var driverSeat = mock(ArmorStand.class);
        when(entities.firstAvailable(true)).thenReturn(0);
        when(entities.seat(0)).thenReturn(driverSeat);
        when(driverSeat.addPassenger(player)).thenReturn(true);
        instance.entities = entities;
        module.enter(player, instance, true);
        when(entities.driver()).thenReturn(player);
        assertEquals(instance.id, module.mountedVehicle(player));
        assertTrue(instance.body.engineRunning);
        module.setHudVisible(player, instance.id, false);
        assertFalse(module.hudVisible(player));
        module.setHudVisible(player, instance.id, true);
        assertTrue(module.hudVisible(player));
        verifyNoInteractions(menus);

        var transmission = instance.body.transmission;
        transmission.gear = 1;
        transmission.targetGear = 1;
        transmission.shiftRemaining = 0;
        when(menus.current(player)).thenReturn(Optional.empty());
        var automaticScroll = new PlayerItemHeldEvent(player, 0, 8);
        module.onHeld(automaticScroll);
        assertFalse(automaticScroll.isCancelled());
        var switchMode = swap();
        module.onSwap(switchMode);
        assertTrue(switchMode.isCancelled());
        assertEquals(VehicleTransmission.Mode.MANUAL, transmission.mode);

        when(menus.current(player)).thenReturn(Optional.of(mock(FloatingMenuHandle.class)));
        var menuScroll = new PlayerItemHeldEvent(player, 0, 8);
        var menuSwap = swap();
        module.onHeld(menuScroll);
        module.onSwap(menuSwap);
        assertFalse(menuScroll.isCancelled());
        assertFalse(menuSwap.isCancelled());
        assertEquals(1, transmission.targetGear);
        assertEquals(VehicleTransmission.Mode.MANUAL, transmission.mode);

        when(menus.current(player)).thenReturn(Optional.empty());
        when(player.isSneaking()).thenReturn(true);
        var shortcut = swap();
        module.onSwap(shortcut);
        assertFalse(shortcut.isCancelled());
        assertEquals(VehicleTransmission.Mode.MANUAL, transmission.mode);
        var drivingScroll = new PlayerItemHeldEvent(player, 0, 8);
        module.onHeld(drivingScroll);
        assertTrue(drivingScroll.isCancelled());
        assertEquals(2, transmission.targetGear);
    }

    private void assertCannotBoard(boolean drive, VehicleModule.Block reason) {
        assertEquals(reason, module.boardingBlock(player, instance, drive));
        assertEquals(reason.name(), assertThrows(IllegalArgumentException.class, () -> module.enter(player, instance, drive)).getMessage());
    }

    private void assertCannotService(boolean fuel, VehicleModule.Block reason) {
        assertEquals(reason, module.serviceBlock(player, instance, fuel));
        assertEquals(reason.name(), assertThrows(IllegalArgumentException.class, () -> module.service(player, instance.id, fuel)).getMessage());
    }

    private PlayerSwapHandItemsEvent swap() { return new PlayerSwapHandItemsEvent(player, mock(ItemStack.class), mock(ItemStack.class)); }
}
