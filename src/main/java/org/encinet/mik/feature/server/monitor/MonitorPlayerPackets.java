package org.encinet.mik.feature.server.monitor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSpectate;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSpectateEntity;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Preserves ordinary Tab entries and rejects spectator client targeting of monitors. */
class MonitorPlayerPackets extends PacketListenerAbstract {
    private final Map<UUID, com.github.retrooper.packetevents.protocol.player.GameMode> modes = new ConcurrentHashMap<>();

    private final java.util.Set<UUID> staff = ConcurrentHashMap.newKeySet();
    private final Map<Integer, UUID> entityIds = new ConcurrentHashMap<>();

    MonitorPlayerPackets() {
        super(PacketListenerPriority.HIGH);
    }

    void enable() { PacketEvents.getAPI().getEventManager().registerListener(this); }

    void disable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        modes.clear();
        staff.clear();
        entityIds.clear();
    }

    void begin(Player player, GameMode original) {
        entityIds.put(player.getEntityId(), player.getUniqueId());
        modes.put(player.getUniqueId(), com.github.retrooper.packetevents.protocol.player.GameMode.valueOf(
                (original == GameMode.SPECTATOR ? GameMode.SURVIVAL : original).name()));
    }

    void end(Player player) {
        modes.remove(player.getUniqueId());
        entityIds.remove(player.getEntityId(), player.getUniqueId());
    }

    void staff(UUID id, boolean permitted) {
        if (permitted) staff.add(id); else staff.remove(id);
    }

    boolean blocksSpectate(UUID viewer, UUID target) {
        return modes.containsKey(target) && !staff.contains(viewer);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (modes.isEmpty() || staff.contains(event.getUser().getUUID())) return;
        if (event.getPacketType() == PacketType.Play.Client.SPECTATE) {
            UUID target = new WrapperPlayClientSpectate(event).getTargetUUID();
            if (blocksSpectate(event.getUser().getUUID(), target)) event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.SPECTATE_ENTITY) {
            var packet = new WrapperPlayClientSpectateEntity(event);
            if (packet.hasEntityId() && entityIds.containsKey(packet.getEntityId())) event.setCancelled(true);
        }
    }

    void refreshMode(Player subject) {
        var entry = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(subject.getUniqueId());
        entry.setGameMode(com.github.retrooper.packetevents.protocol.player.GameMode.valueOf(subject.getGameMode().name()));
        var packet = new WrapperPlayServerPlayerInfoUpdate(
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE, List.of(entry));
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        }
    }

    void showTab(Player viewer, Player subject) {
        // Include the real profile and chat session so keeping Tab does not break signed chat.
        ((CraftPlayer) viewer).getHandle().connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
                List.of(((CraftPlayer) subject).getHandle())));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (modes.isEmpty() || event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE) return;
        var packet = new WrapperPlayServerPlayerInfoUpdate(event);
        if (maskModes(packet)) event.markForReEncode(true);
    }

    boolean maskModes(WrapperPlayServerPlayerInfoUpdate packet) {
        if (!packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE)) return false;
        boolean changed = false;
        for (var entry : packet.getEntries()) {
            var mode = modes.get(entry.getProfileId());
            if (mode != null) {
                entry.setGameMode(mode);
                changed = true;
            }
        }
        return changed;
    }
}
