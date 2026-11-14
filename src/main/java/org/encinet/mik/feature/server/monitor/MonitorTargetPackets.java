package org.encinet.mik.feature.server.monitor;

import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.protocol.vector.positionpath.LinearPositionPath;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-viewer movement decoder. Its base always follows the original wire stream, never Bukkit positions. */
final class MonitorTargetPackets {
    private final Map<UUID, Target> targets = new ConcurrentHashMap<>();

    void begin(UUID viewer, int entity, Vector3d base, float yaw, float pitch) {
        targets.put(viewer, new Target(entity, base, yaw, pitch));
    }

    void end(UUID viewer) { targets.remove(viewer); }

    void clear() { targets.clear(); }

    boolean active(UUID viewer) { return targets.containsKey(viewer); }

    PacketWrapper<?> rewrite(UUID viewer, PacketWrapper<?> packet) {
        Target target = targets.get(viewer);
        return target == null ? packet : target.rewrite(packet);
    }

    private static final class Target {
        private final int entity;
        private Vector3d base;
        private float yaw;
        private float pitch;

        Target(int entity, Vector3d base, float yaw, float pitch) {
            this.entity = entity;
            this.base = base;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        synchronized PacketWrapper<?> rewrite(PacketWrapper<?> packet) {
            boolean grounded;
            if (packet instanceof WrapperPlayServerEntityRelativeMove movement && movement.getEntityId() == entity) {
                base = movement.getDelta().apply(base);
                grounded = movement.isOnGround();
            } else if (packet instanceof WrapperPlayServerEntityRelativeMoveAndRotation movement && movement.getEntityId() == entity) {
                base = movement.getDelta().apply(base);
                yaw = movement.getYaw();
                pitch = movement.getPitch();
                grounded = movement.isOnGround();
            } else if (packet instanceof WrapperPlayServerEntityPositionSync movement && movement.getId() == entity) {
                base = movement.getPosition().getEndPosition();
                yaw = movement.getYRot();
                pitch = movement.getXRot();
                grounded = movement.isOnGround();
                if (movement.getPosition() instanceof LinearPositionPath) return packet;
            } else {
                if (packet instanceof WrapperPlayServerSpawnEntity spawn && spawn.getEntityId() == entity) {
                    base = spawn.getPosition();
                    yaw = spawn.getYaw();
                    pitch = spawn.getPitch();
                } else if (packet instanceof WrapperPlayServerEntityRotation rotation && rotation.getEntityId() == entity) {
                    yaw = rotation.getYaw();
                    pitch = rotation.getPitch();
                }
                return packet;
            }
            // Absolute endpoints also handle a stepped delta whose total exceeds the relative short range.
            // Keeping the original decoded endpoint preserves the next vanilla relative packet's base.
            return new WrapperPlayServerEntityPositionSync(entity, new LinearPositionPath(base), yaw, pitch, grounded);
        }
    }
}
