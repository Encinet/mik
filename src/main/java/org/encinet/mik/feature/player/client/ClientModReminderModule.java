package org.encinet.mik.feature.player.client;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Observes client announcements without advertising or simulating another mod's server protocol. */
public final class ClientModReminderModule implements Listener {
    private static final long REMINDER_DELAY_TICKS = 200L;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final Predicate<UUID> bedrockPlayer;
    private final Predicate<Player> voiceClientPresent;
    // Key by connection, so configuration-stage signals survive join and never leak into a reconnect.
    private final Map<User, Set<RecommendedClientMod>> detected = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final PacketListener packetListener = new PacketListener();
    private EventManager eventManager;

    public ClientModReminderModule(JavaPlugin plugin, LanguageService languageService,
                                   Predicate<UUID> bedrockPlayer, Predicate<Player> voiceClientPresent) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.bedrockPlayer = bedrockPlayer;
        this.voiceClientPresent = voiceClientPresent;
    }

    public void enable() {
        if (eventManager != null) return;
        eventManager = PacketEvents.getAPI().getEventManager();
        eventManager.registerListener(packetListener);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (eventManager != null) {
            eventManager.unregisterListener(packetListener);
            eventManager = null;
        }
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        detected.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        cancelReminder(player.getUniqueId());
        if (bedrockPlayer.test(player.getUniqueId())) return;
        User connection = PacketEvents.getAPI().getPlayerManager().getUser(player);
        pending.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline() || bedrockPlayer.test(player.getUniqueId())) return;
            if (connection != PacketEvents.getAPI().getPlayerManager().getUser(player)) return;
            sendReminder(player, connection);
        }, REMINDER_DELAY_TICKS));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelReminder(event.getPlayer().getUniqueId());
    }

    private void cancelReminder(UUID playerId) {
        BukkitTask task = pending.remove(playerId);
        if (task != null) task.cancel();
    }

    private void sendReminder(Player player, User connection) {
        // Without a connection we cannot observe the handshake; avoid an unsupported absence claim.
        if (connection == null) return;
        Set<RecommendedClientMod> installed = EnumSet.noneOf(RecommendedClientMod.class);
        installed.addAll(detected.getOrDefault(connection, Set.of()));
        for (String channel : player.getListeningPluginChannels()) {
            RecommendedClientMod.fromChannel(channel).ifPresent(installed::add);
        }
        Set<RecommendedClientMod> missing = ClientModSignals.missing(installed, voiceClientPresent.test(player));
        if (missing.isEmpty()) return;

        Component links = Component.join(JoinConfiguration.separator(Component.text(" · ", NamedTextColor.GRAY)),
                missing.stream().map(mod -> Component.text(mod.displayName(), NamedTextColor.YELLOW)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(mod.downloadUrl()))).toList());
        player.sendMessage(languageService.rich(player, Message.CLIENT_MODS_UNDETECTED_RICH, NamedTextColor.GRAY,
                RichArg.component("mods", links)));
    }

    private final class PacketListener extends PacketListenerAbstract {
        private PacketListener() {
            super(PacketListenerPriority.MONITOR);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled()) return;
            Set<RecommendedClientMod> signals;
            if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
                var packet = new WrapperConfigClientPluginMessage(event);
                signals = ClientModSignals.fromPayload(packet.getChannelName(), packet.getData());
            } else if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
                var packet = new WrapperPlayClientPluginMessage(event);
                signals = ClientModSignals.fromPayload(packet.getChannelName(), packet.getData());
            } else return;
            if (!signals.isEmpty()) {
                detected.computeIfAbsent(event.getUser(), ignored -> ConcurrentHashMap.newKeySet()).addAll(signals);
            }
        }

        @Override
        public void onUserDisconnect(UserDisconnectEvent event) {
            detected.remove(event.getUser());
        }
    }
}
