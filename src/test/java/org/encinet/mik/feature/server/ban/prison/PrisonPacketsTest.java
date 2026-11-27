package org.encinet.mik.feature.server.ban.prison;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PrisonPacketsTest {
    private final UUID account = UUID.randomUUID();
    private final User user = mock(User.class);
    private final PrisonPackets gate = new PrisonPackets(account::equals);

    PrisonPacketsTest() { when(user.getUUID()).thenReturn(account); }

    @Test
    void forwardsChatMovementAndProtocolAcknowledgements() {
        for (Client type : List.of(Client.CHAT_MESSAGE, Client.CHAT_ACK, Client.CHAT_SESSION_UPDATE,
                Client.CHAT_COMMAND, Client.CHAT_COMMAND_UNSIGNED, Client.PLAYER_POSITION_AND_ROTATION,
                Client.PLAYER_INPUT, Client.TELEPORT_CONFIRM, Client.KEEP_ALIVE, Client.CHUNK_BATCH_ACK,
                Client.PLAYER_LOADED, Client.CONFIGURATION_ACK)) {
            var event = play(type);
            gate.onPacketReceive(event);
            verify(event, never()).setCancelled(true);
        }
    }

    @Test
    void rejectsInteractivePacketsEvenWhenTheyHaveNoBukkitCancellationEvent() {
        for (Client type : List.of(Client.CHANGE_GAME_MODE, Client.PLAYER_ABILITIES, Client.ENTITY_ACTION,
                Client.CUSTOM_CLICK_ACTION, Client.SELECT_BUNDLE_ITEM, Client.INTERACT_ENTITY,
                Client.USE_ITEM, Client.ATTACK, Client.CLICK_WINDOW, Client.PLAYER_DIGGING, Client.TAB_COMPLETE)) {
            var event = play(type);
            gate.onPacketReceive(event);
            verify(event).setCancelled(true);
        }
    }

    @Test
    void deniesChannelRegistrationAndPayloadsInBothDirectionsFromConfigurationOnward() {
        var configuration = receive(ConnectionState.CONFIGURATION, PacketType.Configuration.Client.PLUGIN_MESSAGE);
        var gameplay = play(Client.PLUGIN_MESSAGE);
        for (var event : List.of(configuration, gameplay)) {
            gate.onPacketReceive(event);
            verify(event).setCancelled(true);
        }

        for (var type : List.of(PacketType.Configuration.Server.PLUGIN_MESSAGE, PacketType.Play.Server.PLUGIN_MESSAGE)) {
            var event = mock(PacketSendEvent.class);
            when(event.getPacketType()).thenReturn(type);
            when(event.getUser()).thenReturn(user);
            gate.onPacketSend(event);
            verify(event).setCancelled(true);
        }
    }

    @Test
    void leavesLoginAndConfigurationAuthenticationPacketsAndOtherAccountsAlone() {
        var login = receive(ConnectionState.LOGIN, PacketType.Login.Client.LOGIN_PLUGIN_RESPONSE);
        gate.onPacketReceive(login);
        verify(login, never()).setCancelled(true);
        var dialog = receive(ConnectionState.CONFIGURATION, PacketType.Configuration.Client.CUSTOM_CLICK_ACTION);
        gate.onPacketReceive(dialog);
        verify(dialog, never()).setCancelled(true);

        when(user.getUUID()).thenReturn(UUID.randomUUID());
        var ordinary = play(Client.CLICK_WINDOW);
        gate.onPacketReceive(ordinary);
        verify(ordinary, never()).setCancelled(true);
        var registration = receive(ConnectionState.CONFIGURATION, PacketType.Configuration.Client.PLUGIN_MESSAGE);
        gate.onPacketReceive(registration);
        verify(registration, never()).setCancelled(true);
        var payload = mock(PacketSendEvent.class);
        when(payload.getPacketType()).thenReturn(PacketType.Play.Server.PLUGIN_MESSAGE);
        when(payload.getUser()).thenReturn(user);
        gate.onPacketSend(payload);
        verify(payload, never()).setCancelled(true);
    }

    private PacketReceiveEvent play(Client type) { return receive(ConnectionState.PLAY, type); }

    private PacketReceiveEvent receive(ConnectionState state, PacketTypeCommon type) {
        var event = mock(PacketReceiveEvent.class);
        when(event.getConnectionState()).thenReturn(state);
        when(event.getPacketType()).thenReturn(type);
        when(event.getUser()).thenReturn(user);
        return event;
    }
}
