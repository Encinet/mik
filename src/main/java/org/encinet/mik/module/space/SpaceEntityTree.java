package org.encinet.mik.module.space;

import io.papermc.paper.math.Angle;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Snapshot of one outermost entity and every nested passenger it owns. */
final class SpaceEntityTree {

    private final Entity root;
    private final List<Member> members;
    private final SpaceEntityShape collisionShape;

    private SpaceEntityTree(
            Entity root,
            List<Member> members,
            SpaceEntityShape collisionShape
    ) {
        this.root = root;
        this.members = members;
        this.collisionShape = collisionShape;
    }

    static SpaceEntityTree capture(Entity root) {
        if (root.getVehicle() != null) {
            throw new IllegalArgumentException("A spatial entity tree must start at its outermost root");
        }
        List<Member> members = new ArrayList<>();
        collect(root, members, new HashSet<>());
        return new SpaceEntityTree(
                root, List.copyOf(members), collisionShape(root, members));
    }

    void apply(
            SpaceTransition transition,
            SpaceMovementResolver.Result movement
    ) {
        for (Member member : members) {
            Entity entity = member.entity();
            if (!entity.isValid()) {
                continue;
            }

            boolean isRoot = entity == root;
            SpaceVector look = isRoot
                    ? transition.lookDirection()
                    : transition.transform().mapDirection(member.lookDirection());
            SpaceVector velocity = isRoot
                    ? transition.velocity()
                    : transition.transform().mapVector(member.velocity());
            velocity = movement.clipVelocity(velocity);

            Location rotation = new Location(entity.getWorld(), 0.0, 0.0, 0.0);
            rotation.setDirection(toBukkit(look));
            entity.setRotation(
                    Angle.absolute(rotation.getYaw()),
                    Angle.absolute(rotation.getPitch()));

            if (entity instanceof LivingEntity living && member.bodyDirection() != null) {
                SpaceVector body = transition.transform().mapDirection(member.bodyDirection());
                Location bodyRotation = new Location(entity.getWorld(), 0.0, 0.0, 0.0);
                bodyRotation.setDirection(toBukkit(body));
                living.setBodyYaw(bodyRotation.getYaw());
            }

            entity.setVelocity(toBukkit(velocity));
            entity.setFallDistance(member.fallDistance());
        }
    }

    void stop() {
        for (Member member : members) {
            if (member.entity().isValid()) {
                member.entity().setVelocity(new Vector());
            }
        }
    }

    SpaceEntityShape collisionShape() {
        return collisionShape;
    }

    /** Entities in stable root-first order, used when presenting one atomic traversal. */
    List<Entity> entities() {
        return members.stream().map(Member::entity).toList();
    }

    private static void collect(
            Entity entity,
            List<Member> members,
            Set<UUID> visited
    ) {
        if (!visited.add(entity.getUniqueId())) {
            throw new IllegalStateException("Cyclic passenger relationship for " + entity.getUniqueId());
        }
        Location location = entity.getLocation();
        SpaceVector bodyDirection = entity instanceof LivingEntity living
                ? directionFromYaw(living.getBodyYaw()) : null;
        members.add(new Member(
                entity,
                fromBukkit(location.getDirection()),
                fromBukkit(entity.getVelocity()),
                entity.getFallDistance(),
                bodyDirection));
        for (Entity passenger : entity.getPassengers()) {
            collect(passenger, members, visited);
        }
    }

    private static SpaceEntityShape collisionShape(
            Entity root,
            List<Member> members
    ) {
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (Member member : members) {
            BoundingBox bounds = member.entity().getBoundingBox();
            minimumX = Math.min(minimumX, bounds.getMinX());
            minimumY = Math.min(minimumY, bounds.getMinY());
            minimumZ = Math.min(minimumZ, bounds.getMinZ());
            maximumX = Math.max(maximumX, bounds.getMaxX());
            maximumY = Math.max(maximumY, bounds.getMaxY());
            maximumZ = Math.max(maximumZ, bounds.getMaxZ());
        }
        Location anchor = root.getLocation();
        return SpaceEntityShape.from(
                new BoundingBox(
                        minimumX, minimumY, minimumZ,
                        maximumX, maximumY, maximumZ),
                new SpaceVector(anchor.getX(), anchor.getY(), anchor.getZ()));
    }

    private static SpaceVector directionFromYaw(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new SpaceVector(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static SpaceVector fromBukkit(Vector vector) {
        return new SpaceVector(vector.getX(), vector.getY(), vector.getZ());
    }

    private static Vector toBukkit(SpaceVector vector) {
        return new Vector(vector.x(), vector.y(), vector.z());
    }

    private record Member(
            Entity entity,
            SpaceVector lookDirection,
            SpaceVector velocity,
            float fallDistance,
            SpaceVector bodyDirection
    ) {
    }
}
