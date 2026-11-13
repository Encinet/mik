package org.encinet.mik.feature.server.safety;

import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/** Prevents explosion damage while retaining knockback and non-destructive interactions. */
public final class ExplosionProtectionModule implements Listener {

    private static final Set<String> EXPLOSION_DAMAGE_TYPES = Set.of(
            "minecraft:explosion", "minecraft:player_explosion", "minecraft:bad_respawn_point",
            "minecraft:fireworks", "minecraft:wind_charge"
    );

    private final JavaPlugin plugin;
    private Tag<DamageType> explosionDamageTypes;

    public ExplosionProtectionModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        explosionDamageTypes = plugin.getServer().getTag(
                "damage_types", NamespacedKey.minecraft("is_explosion"), DamageType.class);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ExplosionProtectionModule enabled");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        EntityType type = event.getEntityType();
        if (type == EntityType.WITHER || type == EntityType.ENDER_DRAGON) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        event.setFire(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (destroysBlocks(event.getExplosionResult())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!event.isCancelled() && destroysBlocks(event.getExplosionResult())) {
            event.blockList().clear();
        }
        // Beds and anchors are removed before this event, even if it is cancelled.
        restoreExplodedSource(event.getExplodedBlockState());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.EXPLOSION) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || isExplosionDamage(event.getDamageSource().getDamageType())) {
            event.setDamage(0);
            // These entities break on an explosion regardless of its damage amount.
            EntityType type = event.getEntityType();
            if (type == EntityType.ARMOR_STAND || type == EntityType.END_CRYSTAL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (isExplosionDamage(event.getDamageSource().getDamageType())) {
            event.setDamage(0);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            event.setCancelled(true);
        }
    }

    private static boolean destroysBlocks(ExplosionResult result) {
        return result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY;
    }

    private boolean isExplosionDamage(DamageType damageType) {
        return EXPLOSION_DAMAGE_TYPES.contains(damageType.key().asString())
                || (explosionDamageTypes != null && explosionDamageTypes.isTagged(damageType));
    }

    private static void restoreExplodedSource(BlockState source) {
        Block sourceBlock = source.getBlock();
        if (!isAir(sourceBlock)) {
            return;
        }
        if (source.getType() == Material.RESPAWN_ANCHOR) {
            source.update(true, false);
            return;
        }
        if (!(source.getBlockData() instanceof Bed bed)) {
            return;
        }

        Block otherHalf = sourceBlock.getRelative(
                bed.getPart() == Bed.Part.HEAD ? bed.getFacing().getOppositeFace() : bed.getFacing());
        if (!isAir(otherHalf)) {
            return;
        }

        bed.setOccupied(false);
        Bed otherData = (Bed) bed.clone();
        otherData.setPart(bed.getPart() == Bed.Part.HEAD ? Bed.Part.FOOT : Bed.Part.HEAD);
        source.setBlockData(bed);
        if (source.update(true, false)) {
            otherHalf.setBlockData(otherData, false);
        }
    }

    private static boolean isAir(Block block) {
        return switch (block.getType()) {
            case AIR, CAVE_AIR, VOID_AIR -> true;
            default -> false;
        };
    }
}
