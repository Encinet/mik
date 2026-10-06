package org.encinet.mik.module.safety;

import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Prevents a summoned pearl from teleporting a player assigned as its owner. */
public final class EnderPearlGuard implements Listener {

    private final JavaPlugin plugin;

    public EnderPearlGuard(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (isCommandOwnedEnderPearl(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (isCommandOwnedEnderPearl(event.getEntity())) {
            // A cancelled block hit can still teleport the owner unless the pearl is removed.
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    private static boolean isCommandOwnedEnderPearl(Projectile projectile) {
        return projectile instanceof EnderPearl
                && projectile.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND
                && projectile.getOwnerUniqueId() != null;
    }
}
