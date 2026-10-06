package org.encinet.mik.module.plot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.encinet.mik.module.plot.protection.PlotProtection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotProtectionPermissionsTest {
    @TempDir Path directory;
    private final UUID worldId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getUID")) return worldId;
                throw new AssertionError(method.getName());
            });
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> actor;
                case "hasPermission" -> false;
                default -> throw new AssertionError(method.getName());
            });

    @Test
    void placingAndBreakingAreEnforcedSeparatelyEvenForCollaborators() throws Exception {
        try (PlotRepository store = store()) {
            PlotRegistry registry = registry(store, Map.of("collaborator.place", true, "collaborator.break", false));
            PlotProtection guard = new PlotProtection(registry, null);
            Block block = block(Material.STONE, false);
            BlockPlaceEvent place = new BlockPlaceEvent(block, block.getState(), block, null, player, true, EquipmentSlot.HAND);
            guard.place(place);
            assertFalse(place.isCancelled());
            BlockBreakEvent broken = new BlockBreakEvent(block, player);
            guard.breakBlock(broken);
            assertTrue(broken.isCancelled());
        }
    }

    @Test
    void deniedStorageCannotBeBypassedByDestroyingTheContainerOrBulkEditing() throws Exception {
        try (PlotRepository store = store()) {
            PlotRegistry registry = registry(store, Map.of("collaborator.container", false));
            PlotProtection guard = new PlotProtection(registry, null);
            BlockBreakEvent chest = new BlockBreakEvent(block(Material.CHEST, true), player);
            guard.breakBlock(chest);
            assertTrue(chest.isCancelled());
            BlockBreakEvent ordinary = new BlockBreakEvent(block(Material.STONE, false), player);
            guard.breakBlock(ordinary);
            assertFalse(ordinary.isCancelled());
            assertFalse(registry.allowed(worldId, 0, 64, 0, actor, false, false, "build"));
        }
    }

    @Test
    void doorsAndPressurePlatesFollowDifferentRules() throws Exception {
        try (PlotRepository store = store()) {
            PlotProtection guard = new PlotProtection(registry(store, Map.of("collaborator.door", true,
                    "collaborator.switch", false, "collaborator.modify", false)), null);
            PlayerInteractEvent door = click(Material.OAK_DOOR, Action.RIGHT_CLICK_BLOCK);
            guard.interact(door);
            assertFalse(door.useInteractedBlock() == Event.Result.DENY);
            PlayerInteractEvent plate = click(Material.OAK_PRESSURE_PLATE, Action.PHYSICAL);
            guard.interact(plate);
            assertEquals(Event.Result.DENY, plate.useInteractedBlock());
            PlayerInteractEvent farmland = click(Material.FARMLAND, Action.PHYSICAL);
            guard.interact(farmland);
            assertEquals(Event.Result.DENY, farmland.useInteractedBlock());
        }
    }

    @Test
    void mountEventChecksRidingRatherThanGenericEntityAccess() throws Exception {
        try (PlotRepository store = store()) {
            PlotProtection guard = new PlotProtection(registry(store, Map.of("collaborator.ride", false,
                    "collaborator.entity_interact", true)), null);
            EntityMountEvent event = new EntityMountEvent(player, entity(Entity.class));
            guard.mount(event);
            assertTrue(event.isCancelled());
        }
    }

    @Test
    void playerAndProjectileDamageUseTheSamePermission() throws Exception {
        try (PlotRepository store = store()) {
            PlotProtection guard = new PlotProtection(registry(store, Map.of("collaborator.entity_damage", false)), null);
            Entity target = entity(Entity.class);
            DamageSource source = (DamageSource) Proxy.newProxyInstance(DamageSource.class.getClassLoader(),
                    new Class<?>[]{DamageSource.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getCausingEntity")) return null;
                        throw new AssertionError(method.getName());
                    });
            EntityDamageByEntityEvent melee = new EntityDamageByEntityEvent(player, target,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 1.0);
            guard.damage(melee);
            assertTrue(melee.isCancelled());
            EntityDamageByEntityEvent arrow = new EntityDamageByEntityEvent(entity(Projectile.class), target,
                    EntityDamageEvent.DamageCause.PROJECTILE, source, 1.0);
            guard.damage(arrow);
            assertTrue(arrow.isCancelled());
        }
    }

    private PlotRepository store() throws Exception {
        PlotRepository store = new PlotRepository(directory.resolve("protection.db"));
        store.open();
        return store;
    }

    private PlotRegistry registry(PlotRepository store, Map<String, Boolean> flags) throws Exception {
        PlotRegistry registry = new PlotRegistry(store);
        registry.put(new Plot(UUID.randomUUID(), worldId, UUID.randomUUID(), "Protected", false, 0,
                Set.of(PlotGeometry.Cell.at(0, 64, 0)), Map.of(actor, Plot.Role.COLLABORATOR), flags, null));
        return registry;
    }

    private PlayerInteractEvent click(Material material, Action action) {
        return new PlayerInteractEvent(player, action, null, block(material, false), BlockFace.UP, EquipmentSlot.HAND);
    }

    private Block block(Material material, boolean container) {
        Class<?>[] types = container ? new Class<?>[]{BlockState.class, InventoryHolder.class}
                : new Class<?>[]{BlockState.class};
        BlockState state = (BlockState) Proxy.newProxyInstance(BlockState.class.getClassLoader(), types,
                (proxy, method, arguments) -> { throw new AssertionError(method.getName()); });
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX", "getZ" -> 0;
                    case "getY" -> 64;
                    case "getType" -> material;
                    case "getState" -> state;
                    default -> throw new AssertionError(method.getName());
                });
    }

    private Entity entity(Class<? extends Entity> type) {
        return (Entity) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getLocation" -> new Location(world, 0, 64, 0);
                    case "getShooter" -> player;
                    default -> throw new AssertionError(method.getName());
                });
    }
}
