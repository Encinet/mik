package org.encinet.mik.module.player;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class GameModeSwitchModule implements Listener {

    private static final byte GAME_MODE_SWITCHER_OP_LEVEL = 2;

    private final JavaPlugin plugin;
    private boolean enabled;

    public GameModeSwitchModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (enabled) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        enabled = true;

        plugin.getLogger().info("GameModeSwitchModule enabled");
    }

    public void disable() {
        if (!enabled) {
            return;
        }
        HandlerList.unregisterAll(this);
        enabled = false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendOpLevel(GAME_MODE_SWITCHER_OP_LEVEL);
    }
}
