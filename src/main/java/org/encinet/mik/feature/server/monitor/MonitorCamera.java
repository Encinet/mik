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
    private static final double MINIMUM_DISTANCE = 1.25;
    private static final double COMFORTABLE_DISTANCE = 3.5;
    private static final double CLEARANCE = 0.35;
    private static final double RADIUS = 0.25;
    private static final double WALL_MARGIN = 0.12;
    private static final double BLEND = 0.2;
    private static final double[] ANGLES = {70, -70, 90, -90, 110, -110, 35, -35, 0, 180};
    private static final double[] HEIGHTS = {1.2, 0.15};
    private static final int RECOVERY_FRAMES = 20;
    private static final int SWITCH_FRAMES = 5;
    private static final double SWITCH_ADVANTAGE = 0.6;
    private static final double TRANSITION_STEP = 0.25;
    private static final int MAX_TRANSITION_STEPS = 128;
    private static final double MINIMUM_AIM_DOT = Math.cos(Math.toRadians(12));

    private final Predicate<Location> solidRender;
    private final MonitorFraming framing = new MonitorFraming();

    private UUID subject;
    private Location previousTarget;
    private Location previousFocus;
    private double heading; // Keep one observation side; strafing or reversing must not orbit the camera.
    private int preferred;
    private int pending = -1;
    private int pendingFrames;
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

    record Frame(Location feet, boolean cut) { }

    void reset() {
        subject = null;
        previousTarget = null;
        previousFocus = null;
        firstPerson = false;
        clearFrames = 0;
        pending = -1;
        pendingFrames = 0;
        framing.reset();
    }

    /** Returns an explicit continuous/cut frame, or null for native first-person spectating. */
    @Nullable Frame update(UUID targetId, Location targetEye, double targetEyeHeight,
                           Location currentEye, double eyeHeight) {
        boolean cut = !targetId.equals(subject) || previousTarget == null
                || !targetEye.getWorld().equals(previousTarget.getWorld());
        if (cut) {
            subject = targetId;
            heading = targetEye.getYaw();
            preferred = 0;
            firstPerson = false;
            clearFrames = 0;
            pending = -1;
            pendingFrames = 0;
            framing.reset();
        }
        // Keep the focus inside a swimming/flying player's body as well as a standing one.
        Location focus = targetEye.clone().subtract(0, Math.min(0.4, targetEyeHeight * 0.25), 0);
        Vector movement = cut ? new Vector() : targetEye.toVector().subtract(previousTarget.toVector());
        MonitorFraming.Frame motion = framing.update(movement);
        Location oldFocus = previousFocus;
        previousTarget = targetEye.clone();
        previousFocus = focus.clone();

        Location best = candidate(targetEye, focus, preferred, motion);
        int winner = preferred;
        Location alternative = best;
        double bestScore = best == null ? Double.NEGATIVE_INFINITY : score(best, focus, preferred, motion.distance());
        if (best == null || best.distance(focus) < COMFORTABLE_DISTANCE) {
            for (int index = 0; index < ANGLES.length * HEIGHTS.length; index++) {
                if (index == preferred) continue;
                Location candidate = candidate(targetEye, focus, index, motion);
                if (candidate == null) continue;
                double candidateScore = score(candidate, focus, index, motion.distance()) - SWITCH_ADVANTAGE;
                if (candidateScore > bestScore) {
                    alternative = candidate;
                    bestScore = candidateScore;
                    winner = index;
                }
            }
        }
        if (winner != preferred) {
            pendingFrames = pending == winner ? pendingFrames + 1 : 1;
            pending = winner;
            // A valid but cramped view can wait; losing sight of the subject cannot.
            if (cut || best == null || pendingFrames >= SWITCH_FRAMES) {
                preferred = winner;
                best = alternative;
                pending = -1;
                pendingFrames = 0;
            }
        } else {
            pending = -1;
            pendingFrames = 0;
        }
        if (best == null) {
            firstPerson = true;
            clearFrames = 0;
            return null;
        }
        if (firstPerson) {
            clearFrames = best.distance(focus) >= 2.5 ? clearFrames + 1 : 0;
            if (clearFrames < RECOVERY_FRAMES) return null;
            firstPerson = false;
            clearFrames = 0;
            cut = true;
        }

        Location eye = best;
        if (!cut && currentEye.getWorld().equals(targetEye.getWorld())) {
            // Carry the full displacement first, so high speed never becomes world-space lag.
            Location carried = currentEye.clone().add(movement);
            double maximumReach = MonitorFraming.MAX_DISTANCE + MonitorFraming.MAX_COMPENSATION + 2;
            if (carried.distanceSquared(targetEye) <= maximumReach * maximumReach) {
                double blend = motion.retracting() ? 0.65 : BLEND;
                Location blended = carried.add(best.toVector().subtract(carried.toVector()).multiply(blend));
                Location clipped = clip(focus, blended);
                if (clipped != null && clipped.distanceSquared(blended) < 0.01) eye = clipped;
                else cut = true;
            } else {
                cut = true;
            }
        } else {
            cut = true;
        }
        eye.setDirection(focus.toVector().subtract(eye.toVector()));
        if (!cut) eye.setYaw((float) (currentEye.getYaw() + wrap(eye.getYaw() - currentEye.getYaw())));
        // Check both occlusion and the aim produced by interpolating position and rotation.
        if (!cut && !visibleTransition(currentEye, eye, oldFocus, focus)) cut = true;
        return new Frame(eye.subtract(0, eyeHeight, 0), cut);
    }

    private double score(Location eye, Location focus, int index, double distance) {
        double turn = Math.abs(wrap(ANGLES[index % ANGLES.length] - ANGLES[preferred % ANGLES.length]));
        return Math.min(distance, eye.distance(focus)) - turn * 0.002
                - (index >= ANGLES.length ? 0.3 : 0);
    }

    private @Nullable Location candidate(Location targetEye, Location focus, int index, MonitorFraming.Frame motion) {
        double angle = Math.toRadians(heading + ANGLES[index % ANGLES.length]);
        Location desired = targetEye.clone().add(Math.sin(angle) * motion.distance(),
                HEIGHTS[index / ANGLES.length], -Math.cos(angle) * motion.distance());
        Location base = clip(focus, desired);
        if (motion.compensation().lengthSquared() < 1.0e-8) return base;
        Location predictedEye = desired.clone().add(motion.compensation());
        Location predicted = clip(focus, predictedEye);
        // Compare lost reach against each requested point, not their distances to the subject:
        // compensation toward the subject is legitimate when both requested views are clear.
        if (predicted == null) return base;
        if (base != null && predicted.distanceSquared(predictedEye) > base.distanceSquared(desired) + 0.01) return base;
        return predicted;
    }

    private boolean visibleTransition(Location fromEye, Location toEye, Location fromFocus, Location toFocus) {
        if (fromFocus == null || !fromEye.getWorld().equals(fromFocus.getWorld())) return false;
        int steps = Math.max(1, (int) Math.ceil(Math.max(fromEye.distance(toEye),
                fromFocus.distance(toFocus)) / TRANSITION_STEP));
        // Bound main-thread work. An unusually long unexplained displacement cuts without changing sides.
        if (steps > MAX_TRANSITION_STEPS) return false;
        Location previous = null;
        boolean previousSolid = false;
        for (int step = 0; step <= steps; step++) {
            double fraction = (double) step / steps;
            Location eye = interpolate(fromEye, toEye, fraction);
            Location focus = interpolate(fromFocus, toFocus, fraction);
            if (!visible(eye, focus)) return false;
            eye.setYaw((float) (fromEye.getYaw() + wrap(toEye.getYaw() - fromEye.getYaw()) * fraction));
            eye.setPitch((float) (fromEye.getPitch() + (toEye.getPitch() - fromEye.getPitch()) * fraction));
            if (eye.getDirection().dot(focus.toVector().subtract(eye.toVector()).normalize()) < MINIMUM_AIM_DOT) return false;
            boolean solid = solidRender.test(eye);
            if (previous != null) {
                // Cut at exposed wall surfaces and thin obstructions between visibility samples.
                if (solid != previousSolid) return false;
                Vector travel = eye.toVector().subtract(previous.toVector());
                double length = travel.length();
                if (!solid && length > 1.0e-8 && eye.getWorld().rayTraceBlocks(previous,
                        travel.multiply(1 / length), length, FluidCollisionMode.NEVER, true) != null) return false;
            }
            previous = eye;
            previousSolid = solid;
        }
        return true;
    }

    private boolean visible(Location eye, Location focus) {
        if (!eye.getWorld().getWorldBorder().isInside(eye) || !loaded(eye, focus)) return false;
        Vector ray = focus.toVector().subtract(eye.toVector());
        double length = ray.length();
        if (length < MINIMUM_DISTANCE) return false;
        if (solidRender.test(eye)) return insideWall(eye) && visibleFromInside(eye, focus);
        return eye.getWorld().rayTraceBlocks(eye, ray.multiply(1 / length), length,
                FluidCollisionMode.NEVER, true) == null;
    }

    private static Location interpolate(Location from, Location to, double fraction) {
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(fraction));
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
        if (!solidRender.test(eye)) return false;
        // Only exposed faces need a margin. Crossing between adjoining solid blocks is stable.
        double x = eye.getX() - Math.floor(eye.getX());
        double y = eye.getY() - Math.floor(eye.getY());
        double z = eye.getZ() - Math.floor(eye.getZ());
        return (x >= WALL_MARGIN || solidRender.test(eye.clone().add(-1, 0, 0)))
                && (x <= 1 - WALL_MARGIN || solidRender.test(eye.clone().add(1, 0, 0)))
                && (y >= WALL_MARGIN || solidRender.test(eye.clone().add(0, -1, 0)))
                && (y <= 1 - WALL_MARGIN || solidRender.test(eye.clone().add(0, 1, 0)))
                && (z >= WALL_MARGIN || solidRender.test(eye.clone().add(0, 0, -1)))
                && (z <= 1 - WALL_MARGIN || solidRender.test(eye.clone().add(0, 0, 1)));
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
