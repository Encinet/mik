package org.encinet.mik.module.safety;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * Prevents players and other entities from trampling fragile blocks.
 *
 * <p>This intentionally targets destructive physical interactions only. Other
 * physical blocks, such as pressure plates and big dripleaves, retain their
 * normal behaviour.
 */
public final class TrampleProtectionModule implements Listener {

    private static final Set<Material> PROTECTED_BLOCKS = Set.of(
            Material.FARMLAND,
            Material.TURTLE_EGG
    );

    private final JavaPlugin plugin;
    private boolean enabled;

    public TrampleProtectionModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (enabled) {
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        enabled = true;
        plugin.getLogger().info("TrampleProtectionModule enabled");
    }

    public void disable() {
        if (!enabled) {
            return;
        }

        HandlerList.unregisterAll(this);
        enabled = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block != null && isProtected(block.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        if (isProtected(event.getBlock().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtected(event.getBlock().getType())) {
            event.setCancelled(true);
        }
    }

    static boolean isProtected(Material material) {
        return PROTECTED_BLOCKS.contains(material);
    }
}
