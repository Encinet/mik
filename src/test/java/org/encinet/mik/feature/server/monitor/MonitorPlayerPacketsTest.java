package org.encinet.mik.feature.server.monitor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSpectate;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSpectateEntity;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitorPlayerPacketsTest {
    @ParameterizedTest
    @MethodSource("unidentifiedPackets")
    void unidentifiedConnectionsBypassReceiveFilteringWhileMonitoringIsActive(PacketTypeCommon type) {
        try (ReceiveFixture f = new ReceiveFixture()) {
            var event = mock(PacketReceiveEvent.class);
            User user = mock(User.class);
            when(event.getUser()).thenReturn(user);
            when(event.getPacketType()).thenReturn(type);
            assertDoesNotThrow(() -> f.tab.onPacketReceive(event));
            verify(event, never()).setCancelled(anyBoolean());
            verify(event, never()).getLastUsedWrapper();
        }
    }

    static Stream<PacketTypeCommon> unidentifiedPackets() {
        return Stream.of(PacketType.Handshaking.Client.HANDSHAKE, PacketType.Status.Client.REQUEST,
                PacketType.Login.Client.LOGIN_START, PacketType.Play.Client.SPECTATE, PacketType.Play.Client.SPECTATE_ENTITY);
    }

    @ParameterizedTest
    @EnumSource(value = PacketType.Play.Client.class, names = {"SPECTATE", "SPECTATE_ENTITY"})
    void identifiedSpectatorRequestsStillRespectMonitorProtectionAndStaffAccess(PacketType.Play.Client type) {
        try (ReceiveFixture f = new ReceiveFixture()) {
            UUID viewer = UUID.randomUUID();
            for (boolean staff : List.of(false, true)) {
                f.tab.staff(viewer, staff);
                for (boolean monitored : List.of(false, true)) {
                    PacketWrapper<?> packet = type == PacketType.Play.Client.SPECTATE
                            ? new WrapperPlayClientSpectate(monitored ? f.monitorId : UUID.randomUUID())
                            : new WrapperPlayClientSpectateEntity(monitored ? 42 : 100);
                    var event = mock(PacketReceiveEvent.class);
                    User user = mock(User.class);
                    when(event.getUser()).thenReturn(user);
                    when(user.getUUID()).thenReturn(viewer);
                    when(user.getClientVersion()).thenReturn(ServerVersion.V_26_3.toClientVersion());
                    when(event.getServerVersion()).thenReturn(ServerVersion.V_26_3);
                    when(event.getPacketType()).thenReturn(type);
                    when(event.getPacketId()).thenReturn(packet.getNativePacketId());
                    doReturn(packet).when(event).getLastUsedWrapper();
                    f.tab.onPacketReceive(event);
                    if (monitored && !staff) verify(event).setCancelled(true);
                    else verify(event, never()).setCancelled(anyBoolean());
                }
            }
        }
    }

    private static final class ReceiveFixture implements AutoCloseable {
        final PacketEventsAPI<?> previous = PacketEvents.getAPI();
        final MonitorPlayerPackets tab = new MonitorPlayerPackets();
        final UUID monitorId = UUID.randomUUID();

        ReceiveFixture() {
            PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
            ServerManager server = mock(ServerManager.class);
            when(api.getServerManager()).thenReturn(server);
            when(server.getVersion()).thenReturn(ServerVersion.V_26_3);
            PacketEvents.setAPI(api);
            Player monitor = mock(Player.class);
            when(monitor.getUniqueId()).thenReturn(monitorId);
            when(monitor.getEntityId()).thenReturn(42);
            tab.begin(monitor, org.bukkit.GameMode.CREATIVE);
        }

        @Override
        public void close() { PacketEvents.setAPI(previous); }
    }

    @Test
    void spectatorUpdatesKeepNormalTabModeNamesAndListingUntilTheSessionEnds() {
        PacketEventsAPI<?> previous = PacketEvents.getAPI();
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        ServerManager server = mock(ServerManager.class);
        when(api.getServerManager()).thenReturn(server);
        when(server.getVersion()).thenReturn(ServerVersion.V_1_21_4);
        PacketEvents.setAPI(api);
        try {
            MonitorPlayerPackets tab = new MonitorPlayerPackets();
            Player player = mock(Player.class);
            UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            tab.begin(player, org.bukkit.GameMode.CREATIVE);
            var login = mock(com.github.retrooper.packetevents.event.PacketSendEvent.class);
            var joining = mock(com.github.retrooper.packetevents.protocol.player.User.class);
            when(login.getUser()).thenReturn(joining);
            assertDoesNotThrow(() -> tab.onPacketSend(login), "connections without a player UUID must bypass target decoding");
            var monitor = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(id);
            monitor.setGameMode(GameMode.SPECTATOR);
            monitor.setListed(true);
            monitor.setDisplayName(Component.text("Staff"));
            var other = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(UUID.randomUUID());
            other.setGameMode(GameMode.SPECTATOR);
            var packet = new WrapperPlayServerPlayerInfoUpdate(
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE, List.of(monitor, other));
            UUID viewer = UUID.randomUUID();
            assertFalse(tab.maskModes(id, packet));
            assertEquals(GameMode.SPECTATOR, monitor.getGameMode(), "Self must retain spectator wall visibility");
            tab.staff(viewer, true);
            assertFalse(tab.maskModes(viewer, packet));
            assertEquals(GameMode.SPECTATOR, monitor.getGameMode());
            tab.staff(viewer, false);
            assertTrue(tab.maskModes(viewer, packet));
            assertEquals(GameMode.CREATIVE, monitor.getGameMode());
            assertEquals(GameMode.SPECTATOR, other.getGameMode());
            assertTrue(monitor.isListed());
            assertEquals(Component.text("Staff"), monitor.getDisplayName());
            assertTrue(tab.blocksSpectate(viewer, id));
            assertFalse(tab.blocksSpectate(viewer, UUID.randomUUID()));
            tab.staff(viewer, true);
            assertFalse(tab.blocksSpectate(viewer, id));
            tab.staff(viewer, false);
            assertTrue(tab.blocksSpectate(viewer, id));
            tab.end(player);
            assertFalse(tab.blocksSpectate(viewer, id));
            monitor.setGameMode(GameMode.SPECTATOR);
            assertFalse(tab.maskModes(viewer, packet));
            assertEquals(GameMode.SPECTATOR, monitor.getGameMode());
        } finally {
            PacketEvents.setAPI(previous);
        }
    }
}
