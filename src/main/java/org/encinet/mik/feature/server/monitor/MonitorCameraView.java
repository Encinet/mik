package org.encinet.mik.feature.server.monitor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/** Viewer-only camera using an empty display, with no player model or skin to render. */
class MonitorCameraView {
    private final BiConsumer<Player, Packet<?>> send;
    private final BiConsumer<Player, PacketWrapper<?>> sendWrapped;
    private final ToIntFunction<World> entityIds;
    private Integer entityId;
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
        // Displays have zero eye height. Keep the desired eye position independently of player size.
        if (relocate) {
            Integer oldEntity = entityId;
            entityId = entityIds.applyAsInt(eye.getWorld());
            // Empty item displays render nothing, including for spectators and in third person.
            // Wall culling still uses the viewer's own spectator state, preserved by MonitorPlayerPackets.
            send.accept(viewer, new ClientboundAddEntityPacket(entityId, UUID.randomUUID(),
                    eye.getX(), eye.getY(), eye.getZ(), eye.getPitch(), eye.getYaw(), EntityTypes.ITEM_DISPLAY,
                    0, Vec3.ZERO, eye.getYaw()));
            // Displays default to instant movement. Configure one tick before attaching so each
            // update interpolates over exactly one monitor interval.
            send.accept(viewer, new ClientboundSetEntityDataPacket(entityId, List.of(SynchedEntityData.DataValue.create(
                    Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID, (int) MonitorCamera.UPDATE_TICKS))));
            sendWrapped.accept(viewer, new WrapperPlayServerCamera(entityId));
            // Attach the new camera before removing the old one; never flash the viewer's own view.
            if (oldEntity != null) {
                send.accept(viewer, new ClientboundRemoveEntitiesPacket(oldEntity));
            }
        } else if (!sameView(eye, previous)) {
            send.accept(viewer, new ClientboundEntityPositionSyncPacket(entityId,
                    PositionPath.of(new Vec3(eye.getX(), eye.getY(), eye.getZ())), eye.getYaw(), eye.getPitch(), false));
        } else {
            return;
        }
        previous = eye;
    }

    void clear(Player viewer) {
        if (entityId == null) return;
        sendWrapped.accept(viewer, new WrapperPlayServerCamera(viewer.getEntityId()));
        send.accept(viewer, new ClientboundRemoveEntitiesPacket(entityId));
        entityId = null;
        previous = null;
    }

    private static boolean sameView(Location first, Location second) {
        return first.distanceSquared(second) < 1.0e-6
                && Math.abs(Math.IEEEremainder(first.getYaw() - second.getYaw(), 360)) < 0.1
                && Math.abs(first.getPitch() - second.getPitch()) < 0.1;
    }
}
