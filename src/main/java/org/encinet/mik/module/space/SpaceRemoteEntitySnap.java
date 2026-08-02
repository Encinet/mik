package org.encinet.mik.module.space;

import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Clears remote-client interpolation after an instantaneous spatial traversal. */
final class SpaceRemoteEntitySnap {

    private SpaceRemoteEntitySnap() {
    }

    static void resetInterpolation(SpaceEntityTree tree) {
        List<Entity> entities = tree.entities();
        Set<UUID> localViewers = new HashSet<>();
        for (Entity entity : entities) {
            if (entity instanceof Player player) {
                localViewers.add(player.getUniqueId());
            }
        }

        Map<Player, List<TrackedMember>> visibleByViewer = new LinkedHashMap<>();
        for (Entity entity : entities) {
            if (!entity.isValid()) {
                continue;
            }
            ChunkMap.TrackedEntity tracker = ((CraftEntity) entity)
                    .getHandle().moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            TrackedMember member = new TrackedMember(entity.getEntityId(), tracker);
            for (Player viewer : entity.getTrackedBy()) {
                // Never remove a player's own client entity. A rider belongs to the
                // traversal and already receives its authoritative local teleport.
                if (!viewer.isOnline() || localViewers.contains(viewer.getUniqueId())) {
                    continue;
                }
                visibleByViewer.computeIfAbsent(viewer, ignored -> new ArrayList<>())
                        .add(member);
            }
        }

        visibleByViewer.forEach(SpaceRemoteEntitySnap::sendBundle);
    }

    private static void sendBundle(Player viewer, List<TrackedMember> members) {
        ServerPlayer observer = ((CraftPlayer) viewer).getHandle();
        List<Packet<? super ClientGamePacketListener>> state = new ArrayList<>();
        List<Packet<? super ClientGamePacketListener>> relations = new ArrayList<>();

        for (TrackedMember member : members) {
            member.tracker().serverEntity.sendPairingData(observer, packet -> {
                if (packet instanceof ClientboundSetPassengersPacket
                        || packet instanceof ClientboundSetEntityLinkPacket) {
                    relations.add(packet);
                } else {
                    state.add(packet);
                }
            });
        }

        List<Packet<? super ClientGamePacketListener>> bundle =
                new ArrayList<>(1 + state.size() + relations.size());
        bundle.add(new ClientboundRemoveEntitiesPacket(
                members.stream().mapToInt(TrackedMember::entityId).toArray()));
        bundle.addAll(state);
        // Relationships must be restored only after every visible entity has
        // been recreated, otherwise the client can discard a reference to an
        // entity that has not reached it yet.
        bundle.addAll(relations);

        // A pathological passenger hierarchy must not disconnect its observers.
        // Ordinary trees are far below the vanilla protocol limit.
        if (bundle.size() > BundlerInfo.BUNDLE_SIZE_LIMIT) {
            return;
        }
        observer.connection.send(new ClientboundBundlePacket(bundle));
    }

    private record TrackedMember(
            int entityId,
            ChunkMap.TrackedEntity tracker
    ) {
    }
}
