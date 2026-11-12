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
import org.encinet.mik.feature.community.chat.bridge.ServerChatPublisher;
import org.encinet.mik.feature.community.chat.model.ChatServerMessage;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Converts actual game connection events into server output, without diffing visible rosters. */
public final class GameChatPresenceListener implements Listener {
    private final Plugin plugin;
    private final ServerChatPublisher publisher;
    private final Predicate<Player> participates;
    private final BiPredicate<UUID, Player> visible;
    private final Set<UUID> participants = new HashSet<>();

    /** A null visibility viewer asks whether an offline account may see the subject. */
    public GameChatPresenceListener(Plugin plugin, ServerChatPublisher publisher,
                                    Predicate<Player> participates, BiPredicate<UUID, Player> visible) {
        this.plugin = plugin;
        this.publisher = publisher;
        this.participates = participates;
        this.visible = visible;
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
        if (participates.test(player) && participants.add(player.getUniqueId())) publish(player, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (participants.remove(player.getUniqueId())) publish(player, false);
    }

    private void publish(Player player, boolean joined) {
        var subject = new ChatServerMessage.Subject(player.getUniqueId(), player.getName());
        var body = new ChatServerMessage.Translation(joined ? ChatServerMessage.Template.GAME_JOINED
                : ChatServerMessage.Template.GAME_LEFT, Map.of("name", subject.name()));
        publisher.publish(ChatServerMessage.publicMessage(body, subject, false),
                new ServerChatPublisher.Visibility(viewer -> visible.test(viewer, player), visible.test(null, player)));
    }
}
