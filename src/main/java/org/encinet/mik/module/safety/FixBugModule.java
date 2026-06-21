package org.encinet.mik.module.safety;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FixBugModule implements Listener {
    private final JavaPlugin plugin;

    public FixBugModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("FixBugModule enabled");
    }

    // Blocks generic.kill style damage against creative/spectator players.
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
           event.setCancelled(true);
        }
    }

    // Cancels negative heal values used by malformed potion effects.
    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent e) {
        if (e.getAmount() < 0) {
            e.setCancelled(true);
        }
    }
}
