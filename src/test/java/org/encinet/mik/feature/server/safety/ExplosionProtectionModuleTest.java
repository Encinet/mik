package org.encinet.mik.feature.server.safety;

import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExplosionProtectionModuleTest {

    private final ExplosionProtectionModule module = new ExplosionProtectionModule(null);

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"WITHER", "ENDER_DRAGON", "CREEPER", "TNT", "COW"})
    void blocksOnlyWitherAndDragonSpawns(EntityType type) {
        Entity entity = mock(Entity.class);
        when(entity.getType()).thenReturn(type);
        EntitySpawnEvent event = new EntitySpawnEvent(entity);

        module.onEntitySpawn(event);

        assertEquals(type == EntityType.WITHER || type == EntityType.ENDER_DRAGON, event.isCancelled());
    }

    @Test
    void removesExplosionFireWithoutCancellingTheExplosion() {
        ExplosionPrimeEvent event = new ExplosionPrimeEvent(mock(Entity.class), 6.0F, true);

        module.onExplosionPrime(event);

        assertFalse(event.getFire());
        assertFalse(event.isCancelled());
        assertEquals(6.0F, event.getRadius());
    }

    @ParameterizedTest
    @EnumSource(ExplosionResult.class)
    void protectsBlocksAndRetainsWindChargeInteractions(ExplosionResult result) {
        Block target = block(Material.STONE);
        EntityExplodeEvent entity = new EntityExplodeEvent(mock(Entity.class), new Location(null, 0, 0, 0),
                new ArrayList<>(List.of(target)), 1.0F, result);
        BlockExplodeEvent block = explosion(snapshot(Material.STONE), result);

        module.onEntityExplode(entity);
        module.onBlockExplode(block);

        int expected = result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY ? 0 : 1;
        assertEquals(expected, entity.blockList().size());
        assertEquals(expected, block.blockList().size());
        assertFalse(entity.isCancelled());
        assertFalse(block.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(BlockIgniteEvent.IgniteCause.class)
    void preventsOnlyExplosionIgnition(BlockIgniteEvent.IgniteCause cause) {
        BlockIgniteEvent event = new BlockIgniteEvent(block(Material.AIR), cause, null, null);

        module.onBlockIgnite(event);

        assertEquals(cause == BlockIgniteEvent.IgniteCause.EXPLOSION, event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(HangingBreakEvent.RemoveCause.class)
    void protectsHangingEntitiesOnlyFromExplosions(HangingBreakEvent.RemoveCause cause) {
        HangingBreakEvent event = new HangingBreakEvent(mock(Hanging.class), cause);

        module.onHangingBreak(event);

        assertEquals(cause == HangingBreakEvent.RemoveCause.EXPLOSION, event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = EntityDamageEvent.DamageCause.class, names = {"BLOCK_EXPLOSION", "ENTITY_EXPLOSION"})
    void zerosExplosionDamageWithoutCancellingKnockback(EntityDamageEvent.DamageCause cause) {
        EntityDamageEvent event = damage(cause, "minecraft:generic");

        module.onEntityDamage(event);

        verify(event).setDamage(0.0);
        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @ValueSource(strings = {"explosion", "player_explosion", "bad_respawn_point", "fireworks", "wind_charge"})
    void alsoProtectsExplosionDamageReportedUnderOtherCauses(String type) {
        EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.CUSTOM, "minecraft:" + type);

        module.onEntityDamage(event);

        verify(event).setDamage(0.0);
        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:fall", "minecraft:lava", "minecraft:player_attack", "custom:explosion"})
    void preservesUnrelatedDamageIncludingNamespacedLookalikes(String type) {
        EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.CUSTOM, type);

        module.onEntityDamage(event);

        verify(event, never()).setDamage(anyDouble());
        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @CsvSource({"ARMOR_STAND, minecraft:explosion, true", "END_CRYSTAL, minecraft:explosion, true",
            "PLAYER, minecraft:explosion, false", "COW, minecraft:explosion, false",
            "ARMOR_STAND, minecraft:player_attack, false", "END_CRYSTAL, minecraft:player_attack, false"})
    void cancelsDestructionForDecorationsWhileRetainingLivingEntityKnockback(
            EntityType victimType, String damageType, boolean cancelled) {
        EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.CUSTOM, damageType, victimType);

        module.onEntityDamage(event);

        assertEquals(cancelled, event.isCancelled());
        if (damageType.equals("minecraft:explosion")) {
            verify(event).setDamage(0.0);
        } else {
            verify(event, never()).setDamage(anyDouble());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:explosion", "minecraft:player_explosion", "minecraft:bad_respawn_point",
            "minecraft:fireworks", "minecraft:wind_charge", "minecraft:player_attack", "minecraft:fall"})
    void protectsVehiclesThroughTheirSeparateDamageEvent(String type) {
        VehicleDamageEvent event = new VehicleDamageEvent(mock(Vehicle.class), damageSource(type), null, 20.0);

        module.onVehicleDamage(event);

        boolean explosion = !type.equals("minecraft:player_attack") && !type.equals("minecraft:fall");
        assertEquals(explosion, event.isCancelled());
        assertEquals(explosion ? 0.0 : 20.0, event.getDamage());
    }

    @Test
    void readsTheServerExplosionTagForDataPackDamageTypes() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        Tag<DamageType> tag = new Tag<>() {
            @Override public boolean isTagged(DamageType type) {
                return type.key().asString().equals("custom:blast");
            }
            @Override public Set<DamageType> getValues() { return Set.of(); }
            @Override public NamespacedKey getKey() { return NamespacedKey.minecraft("is_explosion"); }
        };
        when(server.getTag("damage_types", NamespacedKey.minecraft("is_explosion"), DamageType.class))
                .thenReturn(tag);
        ExplosionProtectionModule enabled = new ExplosionProtectionModule(plugin);
        enabled.enable();
        EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.CUSTOM, "custom:blast");

        enabled.onEntityDamage(event);

        verify(event).setDamage(0.0);
        verify(server.getPluginManager()).registerEvents(enabled, plugin);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restoresRespawnAnchorsEvenWhenTheExplosionWasAlreadyCancelled(boolean cancelled) {
        BlockState source = snapshot(Material.RESPAWN_ANCHOR);
        BlockExplodeEvent event = explosion(source, ExplosionResult.DESTROY);
        event.setCancelled(cancelled);

        module.onBlockExplode(event);

        verify(source).update(true, false);
        assertEquals(cancelled, event.isCancelled());
    }

    @ParameterizedTest
    @CsvSource({"HEAD, NORTH, false", "FOOT, NORTH, true", "HEAD, SOUTH, true", "FOOT, SOUTH, false",
            "HEAD, EAST, false", "FOOT, EAST, true", "HEAD, WEST, true", "FOOT, WEST, false"})
    void restoresBothBedHalvesFacingTheRightWayAndClearsOccupancy(
            Bed.Part part, BlockFace facing, boolean cancelled) {
        BlockState source = snapshot(Material.RED_BED);
        Bed data = bed(part, facing, true);
        when(source.getBlockData()).thenReturn(data);
        Block other = block(Material.AIR);
        when(source.getBlock().getRelative(part == Bed.Part.HEAD ? facing.getOppositeFace() : facing))
                .thenReturn(other);
        BlockExplodeEvent event = explosion(source, ExplosionResult.DESTROY_WITH_DECAY);
        event.setCancelled(cancelled);

        module.onBlockExplode(event);

        verify(source).update(true, false);
        ArgumentCaptor<BlockData> restored = ArgumentCaptor.forClass(BlockData.class);
        verify(other).setBlockData(restored.capture(), eq(false));
        Bed otherData = (Bed) restored.getValue();
        assertEquals(part, data.getPart());
        assertEquals(part == Bed.Part.HEAD ? Bed.Part.FOOT : Bed.Part.HEAD, otherData.getPart());
        assertEquals(facing, otherData.getFacing());
        assertFalse(data.isOccupied());
        assertFalse(otherData.isOccupied());
        assertEquals(cancelled, event.isCancelled());
    }

    @Test
    void neverOverwritesAnOccupiedBedHalfOrAnAlreadyRestoredSource() {
        BlockState source = snapshot(Material.RED_BED);
        when(source.getBlockData()).thenReturn(bed(Bed.Part.HEAD, BlockFace.NORTH, true));
        Block other = block(Material.STONE);
        when(source.getBlock().getRelative(BlockFace.SOUTH)).thenReturn(other);

        module.onBlockExplode(explosion(source, ExplosionResult.DESTROY));

        verify(source, never()).update(anyBoolean(), anyBoolean());
        verify(other, never()).setBlockData(any(BlockData.class), anyBoolean());
        BlockState anchor = snapshot(Material.RESPAWN_ANCHOR);
        when(anchor.getBlock().getType()).thenReturn(Material.RESPAWN_ANCHOR);

        module.onBlockExplode(explosion(anchor, ExplosionResult.DESTROY));

        verify(anchor, never()).update(anyBoolean(), anyBoolean());
    }

    @Test
    void doesNotCreateAnOrphanedBedHalfIfRestoringTheSourceFails() {
        BlockState source = snapshot(Material.RED_BED);
        when(source.getBlockData()).thenReturn(bed(Bed.Part.FOOT, BlockFace.EAST, true));
        when(source.update(true, false)).thenReturn(false);
        Block other = block(Material.AIR);
        when(source.getBlock().getRelative(BlockFace.EAST)).thenReturn(other);

        module.onBlockExplode(explosion(source, ExplosionResult.DESTROY));

        verify(other, never()).setBlockData(any(BlockData.class), anyBoolean());
    }

    private static BlockExplodeEvent explosion(BlockState source, ExplosionResult result) {
        return new BlockExplodeEvent(source.getBlock(), source,
                new ArrayList<>(List.of(block(Material.STONE))), 1.0F, result);
    }

    private static BlockState snapshot(Material material) {
        BlockState state = mock(BlockState.class);
        Block sourceBlock = block(Material.AIR);
        when(state.getType()).thenReturn(material);
        when(state.getBlock()).thenReturn(sourceBlock);
        when(state.getBlockData()).thenReturn(mock(BlockData.class));
        when(state.update(true, false)).thenReturn(true);
        return state;
    }

    private static Block block(Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        return block;
    }

    private static EntityDamageEvent damage(EntityDamageEvent.DamageCause cause, String type) {
        return damage(cause, type, EntityType.PLAYER);
    }

    private static EntityDamageEvent damage(EntityDamageEvent.DamageCause cause, String type, EntityType victimType) {
        // Paper's constructors require deprecated modifier APIs. Keep real cancellation
        // state and verify damage writes without coupling this fixture to those internals.
        EntityDamageEvent event = mock(EntityDamageEvent.class, CALLS_REAL_METHODS);
        doReturn(cause).when(event).getCause();
        doReturn(victimType).when(event).getEntityType();
        doReturn(damageSource(type)).when(event).getDamageSource();
        doNothing().when(event).setDamage(anyDouble());
        return event;
    }

    private static DamageSource damageSource(String type) {
        NamespacedKey key = NamespacedKey.fromString(type);
        DamageType damageType = (DamageType) Proxy.newProxyInstance(DamageType.class.getClassLoader(),
                new Class<?>[]{DamageType.class}, (_, method, _) -> switch (method.getName()) {
                    case "key", "getKey" -> key;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        DamageSource source = mock(DamageSource.class);
        when(source.getDamageType()).thenReturn(damageType);
        return source;
    }

    private static Bed bed(Bed.Part initialPart, BlockFace facing, boolean initialOccupied) {
        AtomicReference<Bed.Part> part = new AtomicReference<>(initialPart);
        AtomicBoolean occupied = new AtomicBoolean(initialOccupied);
        return (Bed) Proxy.newProxyInstance(Bed.class.getClassLoader(), new Class<?>[]{Bed.class},
                (_, method, args) -> switch (method.getName()) {
                    case "getPart" -> part.get();
                    case "setPart" -> { part.set((Bed.Part) args[0]); yield null; }
                    case "getFacing" -> facing;
                    case "isOccupied" -> occupied.get();
                    case "setOccupied" -> { occupied.set((boolean) args[0]); yield null; }
                    case "clone" -> bed(part.get(), facing, occupied.get());
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
