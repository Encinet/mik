package org.encinet.mik.feature.server.safety;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Prevents destructive liquid placement, flow and lava ignition. */
public final class FluidProtectionModule implements Listener {

    private final JavaPlugin plugin;

    public FluidProtectionModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("FluidProtectionModule enabled");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Material source = event.getBlock().getType();
        // This event also covers dragon egg teleportation, which is unrelated to liquids.
        if (source == Material.DRAGON_EGG) {
            return;
        }

        if (wouldDestroy(source, event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Material fluid = bucketFluid(event.getBucket());
        if (fluid != null && wouldDestroy(fluid, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        // Droppers eject bucket items without placing their contents.
        if (event.getBlock().getType() != Material.DISPENSER) {
            return;
        }
        Material fluid = bucketFluid(event.getItem().getType());
        if (fluid != null && event.getBlock().getBlockData() instanceof Dispenser dispenser
                && wouldDestroy(fluid, event.getBlock().getRelative(dispenser.getFacing()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            event.setCancelled(true);
        }
    }

    private static Material bucketFluid(Material bucket) {
        return switch (bucket) {
            case WATER_BUCKET, COD_BUCKET, SALMON_BUCKET, PUFFERFISH_BUCKET,
                    TROPICAL_FISH_BUCKET, AXOLOTL_BUCKET, TADPOLE_BUCKET -> Material.WATER;
            case LAVA_BUCKET -> Material.LAVA;
            default -> null;
        };
    }

    private static boolean wouldDestroy(Material source, Block target) {
        Material type = target.getType();
        if (switch (type) {
            case AIR, CAVE_AIR, VOID_AIR, WATER, LAVA, FIRE, SOUL_FIRE -> true;
            default -> false;
        }) {
            return false;
        }

        // Water can enter these blocks without replacing them. Sources can themselves
        // be waterlogged blocks or aquatic plants, rather than Material.WATER.
        if (source != Material.LAVA && (target.getBlockData() instanceof Waterlogged
                || switch (type) {
                    case KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS, BUBBLE_COLUMN -> true;
                    default -> false;
                })) {
            return false;
        }

        return true;
    }
}
