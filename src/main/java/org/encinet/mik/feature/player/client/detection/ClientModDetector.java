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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** Owns connection observations; callers receive immutable positive evidence rather than advice. */
public final class ClientModDetector implements ClientMods {
    private final EventManager eventManager;
    private final Function<Player, User> connection;
    private final Predicate<Player> voiceClientPresent;
    private final PacketListener packetListener = new PacketListener();
    private volatile Observations observations;

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
        if (observations != null) return;
        observations = new Observations();
        eventManager.registerListener(packetListener);
    }

    public void disable() {
        Observations previous = observations;
        observations = null;
        if (previous != null) {
            synchronized (previous) { previous.connections.clear(); }
        }
        eventManager.unregisterListener(packetListener);
    }

    @Override
    public Optional<Set<ClientMod>> detected(Player player) {
        Observations currentObservations = observations;
        if (currentObservations == null) return Optional.empty();
        User current = connection.apply(player);
        if (current == null) return Optional.empty();
        Set<ClientMod> present = EnumSet.noneOf(ClientMod.class);
        for (String channel : player.getListeningPluginChannels()) {
            ClientModSignals.fromChannel(channel).ifPresent(present::add);
        }
        if (voiceClientPresent.test(player)) present.add(ClientMod.PLASMO_VOICE);
        synchronized (currentObservations) {
            if (observations != currentObservations) return Optional.empty();
            Evidence evidence = currentObservations.connections.computeIfAbsent(current, ignored -> new Evidence());
            if (evidence.closed) return Optional.empty();
            return Optional.of(evidence.merge(present));
        }
    }

    void observe(User user, String channel, byte[] data) {
        observe(observations, user, channel, data);
    }

    private void observe(Observations currentObservations, User user, String channel, byte[] data) {
        if (currentObservations == null || observations != currentObservations) return;
        Set<ClientMod> signals = ClientModSignals.fromPayload(channel, data);
        if (signals.isEmpty()) return;
        synchronized (currentObservations) {
            if (observations != currentObservations) return;
            Evidence evidence = currentObservations.connections.computeIfAbsent(user, ignored -> new Evidence());
            if (!evidence.closed) evidence.merge(signals);
        }
    }

    private final class PacketListener extends PacketListenerAbstract {
        private PacketListener() { super(PacketListenerPriority.MONITOR); }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            Observations currentObservations = observations;
            if (currentObservations == null || event.isCancelled()) return;
            if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
                var packet = new WrapperConfigClientPluginMessage(event);
                observe(currentObservations, event.getUser(), packet.getChannelName(), packet.getData());
            } else if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
                var packet = new WrapperPlayClientPluginMessage(event);
                observe(currentObservations, event.getUser(), packet.getChannelName(), packet.getData());
            }
        }

        @Override
        public void onUserDisconnect(UserDisconnectEvent event) {
            Observations currentObservations = observations;
            if (currentObservations == null) return;
            synchronized (currentObservations) {
                if (observations != currentObservations) return;
                currentObservations.connections.computeIfAbsent(event.getUser(), ignored -> new Evidence()).close();
            }
        }
    }

    private static final class Observations {
        // Weak keys keep disconnect tombstones available for late packets without retaining old users.
        private final Map<User, Evidence> connections = new WeakHashMap<>();
    }

    private static final class Evidence {
        private final Set<ClientMod> mods = EnumSet.noneOf(ClientMod.class);
        private Set<ClientMod> snapshot = Set.of();
        private boolean closed;

        private Set<ClientMod> merge(Set<ClientMod> signals) {
            if (mods.addAll(signals)) snapshot = Set.copyOf(mods);
            return snapshot;
        }

        private void close() {
            closed = true;
            mods.clear();
            snapshot = Set.of();
        }
    }
}
