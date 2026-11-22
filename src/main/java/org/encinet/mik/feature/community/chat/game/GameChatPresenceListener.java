package org.encinet.mik.feature.community.chat.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Converts actual game connection events into server output, without diffing visible rosters. */
public final class GameChatPresenceListener implements Listener {
    private final Plugin plugin;
    private final BiConsumer<Player, Boolean> publishPresence;
    private final Predicate<Player> participates;
    private final Set<UUID> participants = new HashSet<>();

    public GameChatPresenceListener(Plugin plugin, BiConsumer<Player, Boolean> publishPresence,
                                    Predicate<Player> participates) {
        this.plugin = plugin;
        this.publishPresence = publishPresence;
        this.participates = participates;
    }

    public void enable() {
        Bukkit.getOnlinePlayers().stream().filter(participates).map(Player::getUniqueId).forEach(participants::add);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        participants.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (participates.test(player) && participants.add(player.getUniqueId())) publishPresence.accept(player, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (participants.remove(player.getUniqueId())) publishPresence.accept(player, false);
    }
}
