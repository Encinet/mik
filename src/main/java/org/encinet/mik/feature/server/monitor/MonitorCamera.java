package org.encinet.mik.feature.server.monitor;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

/** Main-thread camera placement and collision queries for one patrol session. */
final class MonitorCamera {
    static final long UPDATE_TICKS = 1L;
    private static final double DISTANCE = 4.5;
    private static final double MINIMUM_DISTANCE = 1.25;
    private static final double COMFORTABLE_DISTANCE = 3.5;
    private static final double CLEARANCE = 0.35;
    private static final double RADIUS = 0.25;
    private static final double WALL_MARGIN = 0.12;
    private static final double BLEND = 0.2;
    private static final double[] ANGLES = {70, -70, 90, -90, 110, -110, 35, -35, 0, 180};
    private static final double[] HEIGHTS = {1.2, 0.15};
    private static final int RECOVERY_FRAMES = 20;

    private final Predicate<Location> solidRender;

    private UUID subject;
    private Location previousTarget;
    private double heading; // Keep one observation side; strafing or reversing must not orbit the camera.
    private int preferred;
    private boolean firstPerson;
    private int clearFrames;

    MonitorCamera() {
        this(eye -> ((CraftBlock) eye.getBlock()).getBlockState().isSolidRender());
    }

    MonitorCamera(Predicate<Location> solidRender) {
        // Match the Java client's Camera render-state check exactly. Bukkit's canOcclude
        // and collision shapes also include states that do not enable spectator visibility.
        this.solidRender = solidRender;
    }

    void reset() {
        subject = null;
        previousTarget = null;
        firstPerson = false;
        clearFrames = 0;
    }

    /** Returns a feet position aimed from the actual eye, or null for native first-person spectating. */
    @Nullable Location update(UUID targetId, Location targetEye, Location currentEye, double eyeHeight) {
        boolean snap = !targetId.equals(subject) || previousTarget == null
                || !targetEye.getWorld().equals(previousTarget.getWorld())
                || targetEye.distanceSquared(previousTarget) > 16 * 16;
        if (snap) {
            subject = targetId;
            heading = targetEye.getYaw();
            preferred = 0;
            firstPerson = false;
            clearFrames = 0;
        }
        Vector movement = snap ? new Vector() : targetEye.toVector().subtract(previousTarget.toVector());
        previousTarget = targetEye.clone();

        int previousView = preferred;
        Location best = candidate(targetEye, preferred);
        if (best == null || best.distance(targetEye) < COMFORTABLE_DISTANCE) {
            double score = best == null ? -1 : best.distance(targetEye);
            for (int index = 0; index < ANGLES.length * HEIGHTS.length; index++) {
                if (index == preferred) continue;
                Location alternative = candidate(targetEye, index);
                if (alternative == null) continue;
                double alternativeScore = alternative.distance(targetEye) - 0.6
                        - (index >= ANGLES.length ? 0.3 : 0);
                if (alternativeScore > score) {
                    best = alternative;
                    score = alternativeScore;
                    preferred = index;
                }
            }
        }
        if (best == null) {
            firstPerson = true;
            clearFrames = 0;
            return null;
        }
        if (preferred != previousView) snap = true;
        if (firstPerson) {
            clearFrames = best.distance(targetEye) >= 2.5 ? clearFrames + 1 : 0;
            if (clearFrames < RECOVERY_FRAMES) return null;
            firstPerson = false;
            clearFrames = 0;
            snap = true;
        }

        Location eye = best;
        if (!snap && currentEye.getWorld().equals(targetEye.getWorld())
                && currentEye.distanceSquared(targetEye) <= 8 * 8) {
            // Carry horizontal target movement directly; smooth only orbit corrections and vertical bob.
            // Chasing the previous world position creates lag and then a repeated catch-up wobble.
            Location carried = currentEye.clone().add(movement.getX(), 0, movement.getZ());
            Location blended = carried.add(best.toVector().subtract(carried.toVector()).multiply(BLEND));
            Location clipped = clip(targetEye, blended);
            // Spectators may cross walls when relocating. Validate the new view, not the travel path.
            if (clipped != null && clipped.distanceSquared(blended) < 0.01) eye = clipped;
            else snap = true;
        } else {
            snap = true;
        }
        Location focus = targetEye.clone().subtract(0, 0.4, 0);
        eye.setDirection(focus.toVector().subtract(eye.toVector()));
        if (!snap) {
            eye.setYaw((float) (currentEye.getYaw() + wrap(eye.getYaw() - currentEye.getYaw()) * BLEND));
            eye.setPitch((float) (currentEye.getPitch() + (eye.getPitch() - currentEye.getPitch()) * BLEND));
        }
        return eye.subtract(0, eyeHeight, 0);
    }

    private @Nullable Location candidate(Location targetEye, int index) {
        double angle = Math.toRadians(heading + ANGLES[index % ANGLES.length]);
        return clip(targetEye, targetEye.clone().add(Math.sin(angle) * DISTANCE,
                HEIGHTS[index / ANGLES.length], -Math.cos(angle) * DISTANCE));
    }

    private @Nullable Location clip(Location start, Location desired) {
        Vector delta = desired.toVector().subtract(start.toVector());
        double length = delta.length();
        if (length < MINIMUM_DISTANCE) return null;
        Vector direction = delta.multiply(1 / length);
        // Shrink before ray tracing so neither the ray nor its camera margin enters an unloaded chunk.
        while (length >= MINIMUM_DISTANCE) {
            desired = start.clone().add(direction.clone().multiply(length));
            if (start.getWorld().getWorldBorder().isInside(desired) && loaded(start, desired)) break;
            length -= 0.5;
        }
        if (length < MINIMUM_DISTANCE) return null;

        if (insideWall(desired) && visibleFromInside(desired, start)) return desired;

        RayTraceResult centerHit = start.getWorld().rayTraceBlocks(start, direction.clone(),
                length, FluidCollisionMode.NEVER, true);
        if (centerHit != null) {
            double hitDistance = start.toVector().distance(centerHit.getHitPosition());
            // An air pocket behind a wall cannot use spectator wall visibility. Move the
            // actual camera eye into the intervening wall instead of stopping in that pocket.
            for (double distance = length; distance >= Math.max(MINIMUM_DISTANCE, hitDistance); distance -= 0.1) {
                Location embedded = start.clone().add(direction.clone().multiply(distance));
                if (insideWall(embedded) && visibleFromInside(embedded, start)) return embedded;
            }
        }

        Vector right = new Vector(-direction.getZ(), 0, direction.getX());
        if (right.lengthSquared() < 1.0e-8) right = new Vector(1, 0, 0);
        right.normalize().multiply(RADIUS);
        Vector[] offsets = {new Vector(), right, right.clone().multiply(-1),
                new Vector(0, RADIUS, 0), new Vector(0, -RADIUS, 0)};
        double reach = length;
        for (int index = 0; index < offsets.length; index++) {
            Vector offset = offsets[index];
            Vector ray = desired.toVector().add(offset).subtract(start.toVector());
            double rayLength = ray.length();
            RayTraceResult hit = index == 0 ? centerHit : start.getWorld().rayTraceBlocks(start,
                    ray.multiply(1 / rayLength), rayLength, FluidCollisionMode.NEVER, true);
            if (hit != null) {
                double fraction = start.toVector().distance(hit.getHitPosition()) / rayLength;
                reach = Math.min(reach, length * fraction - CLEARANCE);
            }
        }
        return reach < MINIMUM_DISTANCE ? null : start.clone().add(direction.multiply(reach));
    }

    private boolean insideWall(Location eye) {
        return inset(eye.getX()) && inset(eye.getY()) && inset(eye.getZ()) && solidRender.test(eye);
    }

    private static boolean inset(double coordinate) {
        double fraction = coordinate - Math.floor(coordinate);
        return fraction >= WALL_MARGIN && fraction <= 1 - WALL_MARGIN;
    }

    private boolean visibleFromInside(Location eye, Location target) {
        Vector direction = target.toVector().subtract(eye.toVector());
        double length = direction.length();
        direction.multiply(1 / length);
        double distance = 0;
        // Skip only the contiguous full-block volume containing the camera. Another wall
        // beyond an intervening air pocket can still obscure the player and must be tested.
        for (int step = 0; step < 64 && distance < length; step++) {
            Location cursor = eye.clone().add(direction.clone().multiply(distance));
            if (!solidRender.test(cursor)) {
                return eye.getWorld().rayTraceBlocks(cursor, direction, length - distance,
                        FluidCollisionMode.NEVER, true) == null;
            }
            distance += Math.min(exitDistance(cursor.getX(), direction.getX()),
                    Math.min(exitDistance(cursor.getY(), direction.getY()),
                            exitDistance(cursor.getZ(), direction.getZ()))) + 1.0e-4;
        }
        return distance >= length;
    }

    private static double exitDistance(double coordinate, double direction) {
        if (Math.abs(direction) < 1.0e-8) return Double.POSITIVE_INFINITY;
        return ((direction > 0 ? Math.floor(coordinate) + 1 : Math.floor(coordinate)) - coordinate) / direction;
    }

    private static boolean loaded(Location from, Location to) {
        World world = from.getWorld();
        int minX = (int) Math.floor((Math.min(from.getX(), to.getX()) - CLEARANCE) / 16);
        int maxX = (int) Math.floor((Math.max(from.getX(), to.getX()) + CLEARANCE) / 16);
        int minZ = (int) Math.floor((Math.min(from.getZ(), to.getZ()) - CLEARANCE) / 16);
        int maxZ = (int) Math.floor((Math.max(from.getZ(), to.getZ()) + CLEARANCE) / 16);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x, z)) return false;
            }
        }
        return true;
    }

    private static double wrap(double degrees) {
        return degrees - Math.floor((degrees + 180) / 360) * 360;
    }
}
