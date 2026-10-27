package org.encinet.mik.feature.player.client.detection;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.client.ClientMod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClientModDetectorTest {
    private static PacketEventsAPI<?> previousApi;

    @BeforeAll
    static void provideNativeRegistryResources() {
        previousApi = PacketEvents.getAPI();
        // User initializes PacketEvents' registry tables. Supply its real resource provider without starting a server.
        PacketEvents.setAPI(new PacketEventsAPI<Object>() {
            @Override public boolean isLoaded() { return false; }
            @Override public boolean isInitialized() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public Object getPlugin() { return null; }
            @Override public ServerManager getServerManager() { return () -> ServerVersion.V_1_21_4; }
            @Override public void init() { throw new UnsupportedOperationException(); }
            @Override public ProtocolManager getProtocolManager() { throw new UnsupportedOperationException(); }
            @Override public PlayerManager getPlayerManager() { throw new UnsupportedOperationException(); }
            @Override public NettyManager getNettyManager() { return new NettyManagerImpl(); }
            @Override public ChannelInjector getInjector() { throw new UnsupportedOperationException(); }
        });
    }

    @AfterAll
    static void restoreNativeApi() { PacketEvents.setAPI(previousApi); }

    private final Thread main = Thread.currentThread();
    private final UUID id = UUID.randomUUID();
    private final EventManager events = new EventManager();
    private final AtomicReference<User> connection = new AtomicReference<>(user());
    private final AtomicReference<Set<String>> channels = new AtomicReference<>(Set.of());
    private final AtomicBoolean voice = new AtomicBoolean();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> {
                assertSame(main, Thread.currentThread());
                if (method.getName().equals("getListeningPluginChannels")) return channels.get();
                throw new AssertionError(method.getName());
            });
    private final ClientModDetector detector = new ClientModDetector(events, ignored -> connection.get(), ignored -> voice.get());

    @AfterEach
    void close() { detector.disable(); }

    @Test
    void configurationSignalsRemainAvailableAndReconnectsNeverBorrowTheOldConnectionsEvidence() {
        detector.enable();
        User original = connection.get();
        CompletableFuture.runAsync(() -> detector.observe(original, "minecraft:register",
                "axiom:hello\0plasmo:voice/v2/installed".getBytes(StandardCharsets.UTF_8))).join();
        assertEquals(Set.of(ClientMod.AXIOM, ClientMod.PLASMO_VOICE), detector.detected(player).orElseThrow());

        User replacement = user();
        connection.set(replacement);
        assertTrue(detector.detected(player).orElseThrow().isEmpty());
        detector.observe(replacement, "xaerominimap:main", new byte[0]);
        events.callEvent(new UserDisconnectEvent(original));
        assertEquals(Set.of(ClientMod.XAERO_MINIMAP), detector.detected(player).orElseThrow());
        events.callEvent(new UserDisconnectEvent(replacement));
        assertTrue(detector.detected(player).isEmpty());
    }

    @Test
    void snapshotsMergeNetworkBukkitAndVoiceEvidenceAndRemainImmutable() {
        detector.enable();
        detector.observe(connection.get(), "axiom:hello", new byte[0]);
        channels.set(Set.of("xaerominimap:main"));
        voice.set(true);
        Set<ClientMod> snapshot = detector.detected(player).orElseThrow();
        assertEquals(Set.of(ClientMod.AXIOM, ClientMod.XAERO_MINIMAP, ClientMod.PLASMO_VOICE), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(ClientMod.XAERO_WORLD_MAP));

        detector.observe(connection.get(), "xaeroworldmap:main", new byte[0]);
        assertFalse(snapshot.contains(ClientMod.XAERO_WORLD_MAP));
        assertTrue(detector.detected(player).orElseThrow().contains(ClientMod.XAERO_WORLD_MAP));
    }

    @Test
    void positiveBukkitAndVoiceSignalsRemainKnownForTheConnection() {
        detector.enable();
        channels.set(Set.of("xaerominimap:main", "xaeroworldmap:main"));
        voice.set(true);
        Set<ClientMod> observed = detector.detected(player).orElseThrow();
        channels.set(Set.of());
        voice.set(false);

        assertEquals(Set.of(ClientMod.XAERO_MINIMAP, ClientMod.XAERO_WORLD_MAP, ClientMod.PLASMO_VOICE), observed);
        assertEquals(observed, detector.detected(player).orElseThrow());
        connection.set(user());
        assertTrue(detector.detected(player).orElseThrow().isEmpty());
    }

    @Test
    void voxyEvidenceFromRegisteredReceiversAndReadyPayloadsBelongsToTheCurrentConnection() {
        detector.enable();
        channels.set(Set.of("voxyserver:lod_preserialized"));
        assertEquals(Set.of(ClientMod.VOXY), detector.detected(player).orElseThrow());
        channels.set(Set.of());
        assertEquals(Set.of(ClientMod.VOXY), detector.detected(player).orElseThrow());

        events.callEvent(new UserDisconnectEvent(connection.get()));
        connection.set(user());
        assertTrue(detector.detected(player).orElseThrow().isEmpty());
        detector.observe(connection.get(), "voxyserver:lod_ready", new byte[0]);
        assertEquals(Set.of(ClientMod.VOXY), detector.detected(player).orElseThrow());
    }

    @Test
    void latePacketsCannotReviveADisconnectedConnectionsEvidence() {
        detector.enable();
        User original = connection.get();
        detector.observe(original, "axiom:hello", new byte[0]);
        events.callEvent(new UserDisconnectEvent(original));
        detector.observe(original, "xaerominimap:main", new byte[0]);

        assertTrue(detector.detected(player).isEmpty());
        connection.set(user());
        assertTrue(detector.detected(player).orElseThrow().isEmpty());
    }

    @Test
    void snapshotsCannotMixSignalsAcrossDetectorRestarts() {
        AtomicBoolean restartDuringQuery = new AtomicBoolean(true);
        AtomicReference<ClientModDetector> holder = new AtomicReference<>();
        ClientModDetector restarting = new ClientModDetector(events, ignored -> connection.get(), ignored -> {
            if (!restartDuringQuery.getAndSet(false)) return false;
            holder.get().disable();
            holder.get().enable();
            return true;
        });
        holder.set(restarting);
        try {
            restarting.enable();
            restarting.observe(connection.get(), "axiom:hello", new byte[0]);
            assertTrue(restarting.detected(player).isEmpty());
            assertTrue(restarting.detected(player).orElseThrow().isEmpty());
        } finally {
            restarting.disable();
        }
    }

    @Test
    void noConnectionAndStoppedDetectionAreUnknownAndLateSignalsCannotReviveClosedState() {
        assertTrue(detector.detected(player).isEmpty());
        detector.enable();
        User original = connection.get();
        connection.set(null);
        assertTrue(detector.detected(player).isEmpty());
        connection.set(original);
        detector.observe(original, "axiom:hello", new byte[0]);
        detector.disable();
        detector.observe(original, "xaerominimap:main", new byte[0]);
        assertTrue(detector.detected(player).isEmpty());
        detector.enable();
        assertTrue(detector.detected(player).orElseThrow().isEmpty());
    }

    private User user() {
        return new User(new Object(), ConnectionState.CONFIGURATION, ClientVersion.V_1_21_4, new UserProfile(id, "player"));
    }
}
