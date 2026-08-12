package org.encinet.mik.module.identity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Keeps the cached Minecraft name on bindings current. */
final class IdentityPlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final IdentityBindingRuntime runtime;

    IdentityPlayerListener(JavaPlugin plugin, IdentityBindingRuntime runtime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!runtime.isAvailable()) {
            return;
        }
        try {
            runtime.updatePlayerName(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        } catch (IdentityBindingException error) {
            plugin.getLogger().warning("Could not refresh a bound player name: "
                    + error.getMessage());
        }
    }
}
