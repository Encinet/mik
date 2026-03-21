package org.encinet.mik.module;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class GrieferModule implements Listener {
    private final JavaPlugin plugin;

    public GrieferModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable auto-promote module (called in onEnable)
     */
    public void enable() {
        // Register event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("GrieferModule enabled");
    }

    @EventHandler
    public void onPlayerJoin(EntityDamageByEntityEvent event) {
        // 攻击者是玩家
        if (!(event.getDamager() instanceof Player)) return;
        // 被攻击者是玩家
        if (!(event.getEntity() instanceof Player victim)) return;

        if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
           event.setCancelled(true);
        }
    }
}
