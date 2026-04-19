package org.encinet.mik.module;

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

    /**
     * Enable auto-promote module (called in onEnable)
     */
    public void enable() {
        // Register event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("GrieferModule enabled");
    }

    // fix: generic.kill effect on creative/spectator player
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 攻击者是玩家
        if (!(event.getDamager() instanceof Player)) return;
        // 被攻击者是玩家
        if (!(event.getEntity() instanceof Player victim)) return;

        if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
           event.setCancelled(true);
        }
    }

    // fix: /give @s splash_potion[minecraft:potion_contents={potion: "minecraft:water", custom_effects: [{show_icon: 1b, amplifier: 125b, id: "minecraft:instant_health"}]}]
    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent e) {
        if (e.getAmount() < 0) {
            e.setCancelled(true);
        }
    }
}
