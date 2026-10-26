package org.encinet.mik.feature.player.client.detection;

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
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.client.ClientMod;
import org.encinet.mik.feature.player.client.ClientMods;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Owns connection observations; callers receive immutable positive evidence rather than advice. */
public final class ClientModDetector implements ClientMods {
    private final EventManager eventManager;
    private final Function<Player, User> connection;
    private final Predicate<Player> voiceClientPresent;
    // User identity preserves configuration-stage signals without leaking them into a reconnect.
    private final Map<User, Set<ClientMod>> detected = new HashMap<>();
    private final PacketListener packetListener = new PacketListener();
    private volatile boolean running;

    public ClientModDetector(Predicate<Player> voiceClientPresent) {
        this(PacketEvents.getAPI().getEventManager(),
                player -> PacketEvents.getAPI().getPlayerManager().getUser(player), voiceClientPresent);
    }

    ClientModDetector(EventManager eventManager, Function<Player, User> connection,
                      Predicate<Player> voiceClientPresent) {
        this.eventManager = eventManager;
        this.connection = connection;
        this.voiceClientPresent = voiceClientPresent;
    }

    public void enable() {
        if (running) return;
        running = true;
        eventManager.registerListener(packetListener);
    }

    public void disable() {
        synchronized (detected) {
            running = false;
            detected.clear();
        }
        eventManager.unregisterListener(packetListener);
    }

    @Override
    public Optional<Set<ClientMod>> detected(Player player) {
        if (!running) return Optional.empty();
        User current = connection.apply(player);
        if (current == null) return Optional.empty();
        Set<ClientMod> present = EnumSet.noneOf(ClientMod.class);
        synchronized (detected) {
            if (!running) return Optional.empty();
            present.addAll(detected.getOrDefault(current, Set.of()));
        }
        for (String channel : player.getListeningPluginChannels()) {
            ClientModSignals.fromChannel(channel).ifPresent(present::add);
        }
        if (voiceClientPresent.test(player)) present.add(ClientMod.PLASMO_VOICE);
        return Optional.of(Set.copyOf(present));
    }

    void observe(User user, String channel, byte[] data) {
        if (!running) return;
        Set<ClientMod> signals = ClientModSignals.fromPayload(channel, data);
        if (signals.isEmpty()) return;
        synchronized (detected) {
            if (running) detected.computeIfAbsent(user, ignored -> EnumSet.noneOf(ClientMod.class)).addAll(signals);
        }
    }

    private final class PacketListener extends PacketListenerAbstract {
        private PacketListener() { super(PacketListenerPriority.MONITOR); }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (!running || event.isCancelled()) return;
            if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
                var packet = new WrapperConfigClientPluginMessage(event);
                observe(event.getUser(), packet.getChannelName(), packet.getData());
            } else if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
                var packet = new WrapperPlayClientPluginMessage(event);
                observe(event.getUser(), packet.getChannelName(), packet.getData());
            }
        }

        @Override
        public void onUserDisconnect(UserDisconnectEvent event) {
            synchronized (detected) { detected.remove(event.getUser()); }
        }
    }
}
