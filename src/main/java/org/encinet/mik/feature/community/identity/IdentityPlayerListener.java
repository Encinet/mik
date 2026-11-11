package org.encinet.mik.feature.community.identity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Keeps the cached Minecraft name on bindings current. */
final class IdentityPlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final IdentityBindingRuntime runtime;
    private final Executor writes;

    IdentityPlayerListener(JavaPlugin plugin, IdentityBindingRuntime runtime, Executor writes) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.writes = Objects.requireNonNull(writes, "writes");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!runtime.isAvailable()) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();
        try {
            writes.execute(() -> {
                try {
                    runtime.updatePlayerName(playerId, playerName);
                } catch (IdentityBindingException error) {
                    plugin.getLogger().warning("Could not refresh a bound player name: " + error.getMessage());
                }
            });
        } catch (RejectedExecutionException ignored) {
            // The last known display name remains valid when the queue is full or closing.
        }
    }
}
