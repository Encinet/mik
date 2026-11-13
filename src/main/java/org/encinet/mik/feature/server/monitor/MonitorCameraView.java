package org.encinet.mik.feature.server.monitor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
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

/** Viewer-only spectator player: preserves wall visibility and client movement interpolation. */
class MonitorCameraView {
    private final BiConsumer<Player, Packet<?>> send;
    private final BiConsumer<Player, Integer> selectCamera;
    private final ToIntFunction<World> entityIds;
    private UUID profileId;
    private int entityId;
    private Location previous;

    MonitorCameraView() {
        this((viewer, packet) -> ((CraftPlayer) viewer).getHandle().connection.send(packet),
                (viewer, id) -> PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerCamera(id)),
                world -> ((CraftWorld) world).getHandle().getNextEntityId());
    }

    MonitorCameraView(BiConsumer<Player, Packet<?>> send, BiConsumer<Player, Integer> selectCamera,
                      ToIntFunction<World> entityIds) {
        this.send = send;
        this.selectCamera = selectCamera;
        this.entityIds = entityIds;
    }

    void move(Player viewer, Location feet) {
        Location eye = feet.clone().add(0, viewer.getEyeHeight(), 0);
        boolean relocate = previous == null || !eye.getWorld().equals(previous.getWorld())
                || eye.distanceSquared(previous) > 16 * 16;
        // A remote player has a standing eye height even if the viewer's own pose differs.
        Location body = eye.clone().subtract(0, EntityTypes.PLAYER.getDimensions().eyeHeight(), 0);
        if (relocate) {
            clear(viewer);
            profileId = UUID.randomUUID();
            entityId = entityIds.applyAsInt(eye.getWorld());
            // The client checks the camera PLAYER's spectator state. An invisible armor stand
            // can interpolate, but cannot enable the spectator's inside-block visibility rules.
            var info = new ClientboundPlayerInfoUpdatePacket.Entry(profileId, new GameProfile(profileId, ""),
                    false, 0, GameType.SPECTATOR, null, false, 0, null);
            send.accept(viewer, new ClientboundPlayerInfoUpdatePacket(EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), info));
            send.accept(viewer, new ClientboundAddEntityPacket(entityId, profileId,
                    body.getX(), body.getY(), body.getZ(), body.getPitch(), body.getYaw(), EntityTypes.PLAYER,
                    0, Vec3.ZERO, body.getYaw()));
            selectCamera.accept(viewer, entityId);
        } else if (!sameView(eye, previous)) {
            send.accept(viewer, new ClientboundEntityPositionSyncPacket(entityId,
                    PositionPath.of(new Vec3(body.getX(), body.getY(), body.getZ())), body.getYaw(), body.getPitch(), false));
        }
        previous = eye;
    }

    void clear(Player viewer) {
        if (profileId == null) return;
        selectCamera.accept(viewer, viewer.getEntityId());
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
