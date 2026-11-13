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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitorPlayerPacketsTest {
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
