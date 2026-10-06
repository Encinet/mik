package org.encinet.mik.module.safety;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Removes oversized entities created by any source or loaded from disk. */
public final class OversizedEntityGuard implements Listener {

    private final JavaPlugin plugin;
    private BukkitTask initialScanTask;

    public OversizedEntityGuard(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        initialScanTask = Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    removeIfOversized(entity);
                }
            }
        });
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (initialScanTask != null) {
            initialScanTask.cancel();
            initialScanTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (EntitySizePolicy.isOversized(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityAdded(EntityAddToWorldEvent event) {
        removeIfOversized(event.getEntity());
    }

    private static void removeIfOversized(Entity entity) {
        if (!(entity instanceof Player) && EntitySizePolicy.isOversized(entity)) {
            entity.remove();
        }
    }
}
