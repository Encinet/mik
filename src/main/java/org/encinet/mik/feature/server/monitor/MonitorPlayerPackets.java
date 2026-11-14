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
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.util.Vector3d;
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
    private final MonitorTargetPackets targets = new MonitorTargetPackets();
    private final Map<UUID, FollowTarget> following = new java.util.HashMap<>();

    MonitorPlayerPackets() {
        super(PacketListenerPriority.HIGH);
    }

    void enable() { PacketEvents.getAPI().getEventManager().registerListener(this); }

    void disable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        modes.clear();
        staff.clear();
        entityIds.clear();
        following.clear();
        targets.clear();
    }

    void begin(Player player, GameMode original) {
        entityIds.put(player.getEntityId(), player.getUniqueId());
        modes.put(player.getUniqueId(), com.github.retrooper.packetevents.protocol.player.GameMode.valueOf(
                (original == GameMode.SPECTATOR ? GameMode.SURVIVAL : original).name()));
    }

    void end(Player player) {
        followTarget(player, null);
        modes.remove(player.getUniqueId());
        entityIds.remove(player.getEntityId(), player.getUniqueId());
    }

    /** Main-thread capture; enqueue after previous writes so the decoder starts at their final base. */
    void followTarget(Player viewer, @org.jetbrains.annotations.Nullable Player subject) {
        UUID viewerId = viewer.getUniqueId();
        FollowTarget next = subject == null ? null : new FollowTarget(subject.getUniqueId(), subject.getWorld().getUID(), subject.getEntityId());
        if (java.util.Objects.equals(following.get(viewerId), next)) return;
        if (subject == null) {
            following.remove(viewerId);
            enqueueTarget(viewer, () -> targets.end(viewerId));
            return;
        }
        var handle = ((CraftPlayer) subject).getHandle();
        var tracker = handle.moonrise$getTrackedEntity();
        if (tracker == null) return;
        var base = tracker.serverEntity.getPositionBase();
        var position = new Vector3d(base.x, base.y, base.z);
        int entity = subject.getEntityId();
        float yaw = handle.getYRot();
        float pitch = handle.getXRot();
        following.put(viewerId, next);
        enqueueTarget(viewer, () -> {
            if (modes.containsKey(viewerId)) targets.begin(viewerId, entity, position, yaw, pitch);
        });
    }

    private void enqueueTarget(Player viewer, Runnable update) {
        var channel = ((CraftPlayer) viewer).getHandle().connection.connection.channel;
        try {
            channel.eventLoop().execute(update);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Disconnect may already have shut down the connection's event loop.
            targets.end(viewer.getUniqueId());
        }
    }

    private record FollowTarget(UUID player, UUID world, int entity) { }

    void staff(UUID id, boolean permitted) {
        if (permitted) staff.add(id); else staff.remove(id);
    }

    boolean blocksSpectate(UUID viewer, UUID target) {
        return modes.containsKey(target) && !staff.contains(viewer);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (modes.isEmpty() || event.isCancelled()) return;
        if (event.getPacketType() != PacketType.Play.Client.SPECTATE
                && event.getPacketType() != PacketType.Play.Client.SPECTATE_ENTITY) return;
        UUID viewer = event.getUser().getUUID();
        if (viewer == null || staff.contains(viewer)) return;
        if (event.getPacketType() == PacketType.Play.Client.SPECTATE) {
            UUID target = new WrapperPlayClientSpectate(event).getTargetUUID();
            if (blocksSpectate(viewer, target)) event.setCancelled(true);
        } else if (event.getPacketType() == PacketType.Play.Client.SPECTATE_ENTITY) {
            var packet = new WrapperPlayClientSpectateEntity(event);
            if (packet.hasEntityId() && entityIds.containsKey(packet.getEntityId())) event.setCancelled(true);
        }
    }

    void refreshMode(Player subject) {
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            // Listeners rewrite modes per recipient; never reuse a rewritten entry for staff or self.
            var entry = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(subject.getUniqueId());
            entry.setGameMode(com.github.retrooper.packetevents.protocol.player.GameMode.valueOf(subject.getGameMode().name()));
            var packet = new WrapperPlayServerPlayerInfoUpdate(
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE, List.of(entry));
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
        if (modes.isEmpty() || event.isCancelled()) return;
        UUID viewer = event.getUser().getUUID();
        if (viewer == null) return;
        if (targets.active(viewer)) {
            PacketWrapper<?> movement = switch (event.getPacketType()) {
                case PacketType.Play.Server.ENTITY_RELATIVE_MOVE -> new WrapperPlayServerEntityRelativeMove(event);
                case PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION -> new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                case PacketType.Play.Server.ENTITY_POSITION_SYNC -> new WrapperPlayServerEntityPositionSync(event);
                case PacketType.Play.Server.ENTITY_ROTATION -> new WrapperPlayServerEntityRotation(event);
                case PacketType.Play.Server.SPAWN_ENTITY -> new WrapperPlayServerSpawnEntity(event);
                default -> null;
            };
            if (movement != null) {
                PacketWrapper<?> replacement = targets.rewrite(viewer, movement);
                if (replacement != movement) {
                    event.setCancelled(true);
                    event.getUser().sendPacketSilently(replacement);
                }
            }
        }
        if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE) return;
        var packet = new WrapperPlayServerPlayerInfoUpdate(event);
        if (maskModes(viewer, packet)) event.markForReEncode(true);
    }

    boolean maskModes(UUID viewer, WrapperPlayServerPlayerInfoUpdate packet) {
        if (staff.contains(viewer)) return false;
        if (!packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE)) return false;
        boolean changed = false;
        for (var entry : packet.getEntries()) {
            // Camera wall culling reads LocalPlayer.isSpectator() through its own PlayerInfo.
            if (entry.getProfileId().equals(viewer)) continue;
            var mode = modes.get(entry.getProfileId());
            if (mode != null) {
                entry.setGameMode(mode);
                changed = true;
            }
        }
        return changed;
    }
}
