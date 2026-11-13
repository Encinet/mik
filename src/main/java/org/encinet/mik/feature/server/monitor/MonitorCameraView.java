package org.encinet.mik.feature.server.monitor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/** Viewer-only spectator camera with client movement interpolation. */
class MonitorCameraView {
    private final BiConsumer<Player, Packet<?>> send;
    private final BiConsumer<Player, PacketWrapper<?>> sendWrapped;
    private final ToIntFunction<World> entityIds;
    private UUID profileId;
    private int entityId;
    private Location previous;

    MonitorCameraView() {
        this((viewer, packet) -> ((CraftPlayer) viewer).getHandle().connection.send(packet),
                (viewer, packet) -> PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet),
                world -> ((CraftWorld) world).getHandle().getNextEntityId());
    }

    MonitorCameraView(BiConsumer<Player, Packet<?>> send, BiConsumer<Player, PacketWrapper<?>> sendWrapped,
                      ToIntFunction<World> entityIds) {
        this.send = send;
        this.sendWrapped = sendWrapped;
        this.entityIds = entityIds;
    }

    void move(Player viewer, Location feet, boolean cut) {
        Location eye = feet.clone().add(0, viewer.getEyeHeight(), 0);
        boolean relocate = cut || previous == null || !eye.getWorld().equals(previous.getWorld());
        // A remote player has a standing eye height even if the viewer's own pose differs.
        Location body = eye.clone().subtract(0, EntityTypes.PLAYER.getDimensions().eyeHeight(), 0);
        if (relocate) {
            UUID oldProfile = profileId;
            int oldEntity = entityId;
            profileId = UUID.randomUUID();
            entityId = entityIds.applyAsInt(eye.getWorld());
            // Keep the camera unlisted. Wall culling depends on the viewer's own spectator state,
            // which MonitorPlayerPackets preserves separately from other viewers' Tab display.
            var info = new ClientboundPlayerInfoUpdatePacket.Entry(profileId, new GameProfile(profileId, ""),
                    false, 0, GameType.SPECTATOR, null, false, 0, null);
            send.accept(viewer, new ClientboundPlayerInfoUpdatePacket(EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), info));
            send.accept(viewer, new ClientboundAddEntityPacket(entityId, profileId,
                    body.getX(), body.getY(), body.getZ(), body.getPitch(), body.getYaw(), EntityTypes.PLAYER,
                    0, Vec3.ZERO, body.getYaw()));
            // LivingEntity.getViewYRot reads head yaw, independently of the body's rotation.
            // Initialize it before attaching the camera, then keep it synchronized while moving.
            sendWrapped.accept(viewer, new WrapperPlayServerEntityHeadLook(entityId, body.getYaw()));
            sendWrapped.accept(viewer, new WrapperPlayServerCamera(entityId));
            // Attach the new camera before removing the old one; never flash the viewer's own view.
            if (oldProfile != null) {
                send.accept(viewer, new ClientboundRemoveEntitiesPacket(oldEntity));
                send.accept(viewer, new ClientboundPlayerInfoRemovePacket(List.of(oldProfile)));
            }
        } else if (!sameView(eye, previous)) {
            send.accept(viewer, new ClientboundEntityPositionSyncPacket(entityId,
                    PositionPath.of(new Vec3(body.getX(), body.getY(), body.getZ())), body.getYaw(), body.getPitch(), false));
            sendWrapped.accept(viewer, new WrapperPlayServerEntityHeadLook(entityId, body.getYaw()));
        } else {
            return;
        }
        previous = eye;
    }

    void clear(Player viewer) {
        if (profileId == null) return;
        sendWrapped.accept(viewer, new WrapperPlayServerCamera(viewer.getEntityId()));
        send.accept(viewer, new ClientboundRemoveEntitiesPacket(entityId));
        send.accept(viewer, new ClientboundPlayerInfoRemovePacket(List.of(profileId)));
        profileId = null;
        previous = null;
    }

    private static boolean sameView(Location first, Location second) {
        return first.distanceSquared(second) < 1.0e-6
                && Math.abs(Math.IEEEremainder(first.getYaw() - second.getYaw(), 360)) < 0.1
                && Math.abs(first.getPitch() - second.getPitch()) < 0.1;
    }
}
