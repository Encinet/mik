package org.encinet.mik.feature.server.ban.prison;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

import java.util.UUID;
import java.util.function.Predicate;

/** Only reads the ban snapshot on the network thread. Bukkit still enforces server-side actions. */
final class PrisonPackets extends PacketListenerAbstract {
    private final Predicate<UUID> confined;

    PrisonPackets(Predicate<UUID> confined) {
        super(PacketListenerPriority.LOWEST);
        this.confined = confined;
    }

    @Override public void onPacketReceive(PacketReceiveEvent event) {
        var type = event.getPacketType();
        // The configuration payload also carries channel registrations that survive into play.
        if (type == PacketType.Configuration.Client.PLUGIN_MESSAGE
                || event.getConnectionState() == ConnectionState.PLAY && !allowed(type)) deny(event);
    }

    @Override public void onPacketSend(PacketSendEvent event) {
        var type = event.getPacketType();
        if (type == PacketType.Configuration.Server.PLUGIN_MESSAGE || type == PacketType.Play.Server.PLUGIN_MESSAGE)
            deny(event);
    }

    private void deny(ProtocolPacketEvent event) {
        if (event.isCancelled()) return;
        UUID id = event.getUser().getUUID();
        if (id != null && confined.test(id)) event.setCancelled(true);
    }

    private static boolean allowed(PacketTypeCommon type) {
        if (!(type instanceof PacketType.Play.Client packet)) return false;
        return switch (packet) {
            // The command listener denies execution after Paper has processed signed chat state.
            case CHAT_MESSAGE, CHAT_ACK, CHAT_SESSION_UPDATE, CHAT_COMMAND, CHAT_COMMAND_UNSIGNED,
                    KEEP_ALIVE, PONG, CLIENT_SETTINGS, RESOURCE_PACK_STATUS, COOKIE_RESPONSE, CONFIGURATION_ACK,
                    TELEPORT_CONFIRM, CHUNK_BATCH_ACK, CLIENT_TICK_END, PLAYER_LOADED,
                    PLAYER_POSITION, PLAYER_POSITION_AND_ROTATION, PLAYER_ROTATION, PLAYER_FLYING, PLAYER_INPUT,
                    CLIENT_STATUS, CLOSE_WINDOW -> true;
            default -> false;
        };
    }
}
