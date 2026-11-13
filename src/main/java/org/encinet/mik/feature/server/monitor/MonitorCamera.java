package org.encinet.mik.feature.server.monitor;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Main-thread camera placement and collision queries for one patrol session. */
final class MonitorCamera {
    static final long UPDATE_TICKS = 1L;
    private static final double MINIMUM_DISTANCE = 1.25;
    private static final double COMFORTABLE_DISTANCE = 3.5;
    private static final double CLEARANCE = MonitorSight.MARGIN;
    private static final double RADIUS = 0.25;
    private static final double WALL_MARGIN = 0.12;
    private static final double FOLLOW_DAMPING = 0.5;
    private static final double MAX_FOLLOW_LAG = 0.75;
    private static final int LOOK_STABILITY_FRAMES = 5;
    private static final double OPERATION_REACH = 3;
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
    private final MonitorOperation operations = new MonitorOperation();
    private double minimumFrameDot = Math.cos(Math.toRadians(30));

    private UUID subject;
    private Location previousTarget;
    private Location previousFocus;
    private Location previousAim;
    private MonitorOperation.Focus previousOperation;
    private final Vector velocity = new Vector();
    private double heading;
    private double pitch;
    private double desiredHeading;
    private double desiredPitch;
    private double headingVelocity;
    private double pitchVelocity;
    private float lastYaw;
    private float lastPitch;
    private int yawFrames;
    private int pitchFrames;
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

    MonitorOperation operations() { return operations; }

    void fieldOfView(double degrees) {
        minimumFrameDot = Math.cos(Math.toRadians(Math.clamp(degrees, 30, 110) / 2 - 5));
    }

    void reset() {
        operations.reset();
        subject = null;
        previousTarget = null;
        previousFocus = null;
        previousAim = null;
        previousOperation = null;
        velocity.zero();
        firstPerson = false;
        clearFrames = 0;
        pending = -1;
        pendingFrames = 0;
        framing.reset();
    }

    /** Returns an explicit continuous/cut frame, or null for native first-person spectating. */
    @Nullable Frame update(Player target, Location currentEye, double eyeHeight) {
        Location targetEye = target.getEyeLocation();
        if (!target.getUniqueId().equals(subject) || previousTarget == null
                || !targetEye.getWorld().equals(previousTarget.getWorld())) operations.reset();
        return update(target.getUniqueId(), targetEye, target.getEyeHeight(), currentEye, eyeHeight, operations.sample(target));
    }

    @Nullable Frame update(UUID targetId, Location targetEye, double targetEyeHeight,
                           Location currentEye, double eyeHeight, @Nullable MonitorOperation.Focus observed) {
        boolean cut = !targetId.equals(subject) || previousTarget == null
                || !targetEye.getWorld().equals(previousTarget.getWorld());
        if (cut) {
            subject = targetId;
            heading = targetEye.getYaw();
            pitch = targetEye.getPitch();
            desiredHeading = heading;
            desiredPitch = pitch;
            headingVelocity = 0;
            pitchVelocity = 0;
            lastYaw = targetEye.getYaw();
            lastPitch = targetEye.getPitch();
            yawFrames = 0;
            pitchFrames = 0;
            velocity.zero();
            preferred = 0;
            firstPerson = false;
            clearFrames = 0;
            pending = -1;
            pendingFrames = 0;
            framing.reset();
        } else {
            updateLook(targetEye);
        }
        // Keep the focus inside a swimming/flying player's body as well as a standing one.
        Location focus = targetEye.clone().subtract(0, Math.min(0.4, targetEyeHeight * 0.25), 0);
        Location look = targetEye.clone();
        look.setYaw((float) heading);
        look.setPitch((float) pitch);
        Location operation = targetEye.clone().add(look.getDirection().multiply(OPERATION_REACH));
        Composition composition = new Composition(focus, operation, observed, minimumFrameDot);
        Vector movement = cut ? new Vector() : targetEye.toVector().subtract(previousTarget.toVector());
        MonitorFraming.Frame motion = framing.update(movement);
        Location oldFocus = previousFocus;
        Location oldAim = previousAim;
        previousTarget = targetEye.clone();
        previousFocus = focus.clone();

        Location best = candidate(targetEye, composition, preferred, motion);
        int winner = preferred;
        Location alternative = best;
        double bestScore = best == null ? Double.NEGATIVE_INFINITY : score(best, focus, preferred, motion.distance());
        if (best == null || best.distance(focus) < COMFORTABLE_DISTANCE) {
            for (int index = 0; index < ANGLES.length * HEIGHTS.length; index++) {
                if (index == preferred) continue;
                Location candidate = candidate(targetEye, composition, index, motion);
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
            velocity.zero();
            previousOperation = null;
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
            // Check the translated rig before applying its bounded inertia.
            Location carried = currentEye.clone().add(movement);
            double maximumReach = MonitorFraming.MAX_DISTANCE + MonitorFraming.MAX_COMPENSATION + 2;
            if (carried.distanceSquared(targetEye) <= maximumReach * maximumReach) {
                Location blended = smooth(currentEye, best, motion.retracting());
                Location clipped = clip(focus, blended);
                if (clipped != null && clipped.distanceSquared(blended) < 0.01
                        && usable(clipped, composition)) eye = clipped;
                else cut = true;
            } else {
                cut = true;
            }
        } else {
            cut = true;
        }
        Location aim = composition.aim(eye);
        eye.setDirection(aim.toVector().subtract(eye.toVector()));
        if (!cut) eye.setYaw((float) (currentEye.getYaw() + wrap(eye.getYaw() - currentEye.getYaw())));
        // Check both occlusion and the aim produced by interpolating position and rotation.
        if (!cut && !visibleTransition(currentEye, eye, oldFocus, focus, oldAim, aim, observed)) cut = true;
        previousAim = aim;
        previousOperation = observed;
        if (cut) velocity.zero();
        return new Frame(eye.subtract(0, eyeHeight, 0), cut);
    }

    private void updateLook(Location targetEye) {
        // Brief glances and alternating inputs do not swing the rig around the player.
        yawFrames = Math.abs(wrap(targetEye.getYaw() - lastYaw)) <= 15
                ? Math.min(LOOK_STABILITY_FRAMES, yawFrames + 1) : 0;
        pitchFrames = Math.abs(targetEye.getPitch() - lastPitch) <= 15
                ? Math.min(LOOK_STABILITY_FRAMES, pitchFrames + 1) : 0;
        lastYaw = targetEye.getYaw();
        lastPitch = targetEye.getPitch();
        if (yawFrames >= LOOK_STABILITY_FRAMES && Math.abs(wrap(lastYaw - desiredHeading)) > 20) {
            desiredHeading = heading + wrap(lastYaw - heading);
        }
        if (pitchFrames >= LOOK_STABILITY_FRAMES && Math.abs(lastPitch - desiredPitch) > 10) desiredPitch = lastPitch;
        double desiredTurn = Math.clamp(wrap(desiredHeading - heading) * 0.12, -5, 5);
        headingVelocity += (desiredTurn - headingVelocity) * 0.25;
        heading += headingVelocity;
        double desiredTilt = Math.clamp((desiredPitch - pitch) * 0.12, -3, 3);
        pitchVelocity += (desiredTilt - pitchVelocity) * 0.25;
        pitch = Math.clamp(pitch + pitchVelocity, -90, 90);
    }

    private Location smooth(Location current, Location desired, boolean retracting) {
        Vector error = current.toVector().subtract(desired.toVector());
        // Keep inertia local to the subject, even when a fast vehicle moves many blocks per tick.
        if (error.lengthSquared() > MAX_FOLLOW_LAG * MAX_FOLLOW_LAG) error.normalize().multiply(MAX_FOLLOW_LAG);
        // Integrate a critically damped spring over one monitor tick, retaining its velocity
        // between frames so acceleration and braking settle without oscillation.
        double damping = retracting ? FOLLOW_DAMPING * 1.1 : FOLLOW_DAMPING;
        Vector step = velocity.clone().add(error.clone().multiply(damping));
        double decay = Math.exp(-damping);
        Vector nextError = error.add(step).multiply(decay);
        if (nextError.lengthSquared() > MAX_FOLLOW_LAG * MAX_FOLLOW_LAG) nextError.normalize().multiply(MAX_FOLLOW_LAG);
        velocity.subtract(step.multiply(damping)).multiply(decay);
        return desired.clone().add(nextError);
    }

    private double score(Location eye, Location focus, int index, double distance) {
        double turn = Math.abs(wrap(ANGLES[index % ANGLES.length] - ANGLES[preferred % ANGLES.length]));
        return Math.min(distance, eye.distance(focus)) - turn * 0.002
                - (index >= ANGLES.length ? 0.3 : 0);
    }

    private @Nullable Location candidate(Location targetEye, Composition composition, int index, MonitorFraming.Frame motion) {
        double previousDistance = -1;
        for (int attempt = 0; attempt < (composition.observed() == null ? 1 : 3); attempt++) {
            double distance = Math.min(MonitorFraming.MAX_DISTANCE, motion.distance() + attempt * 1.75);
            if (distance == previousDistance) continue;
            previousDistance = distance;
            Location candidate = candidateAt(targetEye, composition, index, distance, motion.compensation());
            if (candidate != null) return candidate;
        }
        return null;
    }

    private @Nullable Location candidateAt(Location targetEye, Composition composition, int index, double distance, Vector compensation) {
        Location focus = composition.body();
        double angle = Math.toRadians(heading + ANGLES[index % ANGLES.length]);
        Location desired = targetEye.clone().add(Math.sin(angle) * distance,
                HEIGHTS[index / ANGLES.length], -Math.cos(angle) * distance);
        Location base = clip(focus, desired);
        if (base != null && !usable(base, composition)) base = null;
        if (compensation.lengthSquared() < 1.0e-8) return base;
        Location predictedEye = desired.clone().add(compensation);
        Location predicted = clip(focus, predictedEye);
        if (predicted != null && !usable(predicted, composition)) predicted = null;
        // Compare lost reach against each requested point, not their distances to the subject:
        // compensation toward the subject is legitimate when both requested views are clear.
        if (predicted == null) return base;
        if (base != null && predicted.distanceSquared(predictedEye) > base.distanceSquared(desired) + 0.01) return base;
        return predicted;
    }

    private boolean usable(Location eye, Composition composition) {
        return composition.fits(eye) && operationVisible(eye, composition.observed());
    }

    private boolean operationVisible(Location eye, @Nullable MonitorOperation.Focus observed) {
        if (observed == null) return true;
        Location point = observed.point();
        if (!MonitorSight.loaded(eye, point)) return false;
        Vector delta = point.toVector().subtract(eye.toVector());
        double distance = delta.length();
        if (distance < 0.05) return false;
        Vector direction = delta.multiply(1 / distance);
        // Seeing the player does not imply seeing what is hidden behind their body.
        if (observed.subjectBounds().rayTrace(eye.toVector(), direction, distance - 0.04) != null) return false;
        if (solidRender.test(eye)) return insideWall(eye) && visibleFromInside(eye, point);
        return eye.getWorld().rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, true) == null;
    }

    private boolean visibleTransition(Location fromEye, Location toEye, Location fromFocus, Location toFocus,
                                      Location fromAim, Location toAim, @Nullable MonitorOperation.Focus observed) {
        if (fromFocus == null || fromAim == null || !fromEye.getWorld().equals(fromFocus.getWorld())) return false;
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
            Location aim = interpolate(fromAim, toAim, fraction);
            if (!visible(eye, focus)) return false;
            MonitorOperation.Focus during = observed == null ? null : observed.during(previousOperation,
                    focus.toVector().subtract(toFocus.toVector()), fraction);
            if (!operationVisible(eye, during)) return false;
            eye.setYaw((float) (fromEye.getYaw() + wrap(toEye.getYaw() - fromEye.getYaw()) * fraction));
            eye.setPitch((float) (fromEye.getPitch() + (toEye.getPitch() - fromEye.getPitch()) * fraction));
            if (eye.getDirection().dot(aim.toVector().subtract(eye.toVector()).normalize()) < MINIMUM_AIM_DOT) return false;
            if (during != null && !new Composition(focus, focus, during, minimumFrameDot).fits(eye, eye.getDirection())) return false;
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
        if (!eye.getWorld().getWorldBorder().isInside(eye) || !MonitorSight.loaded(eye, focus)) return false;
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
            if (start.getWorld().getWorldBorder().isInside(desired) && MonitorSight.loaded(start, desired)) break;
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

    private record Composition(Location body, Location estimated, @Nullable MonitorOperation.Focus observed,
                               double minimumDot, List<Vector> points) {
        Composition(Location body, Location estimated, @Nullable MonitorOperation.Focus observed, double minimumDot) {
            this(body, estimated, observed, minimumDot, samples(body, estimated, observed));
        }

        Location aim(Location eye) {
            if (observed == null) return body.clone().add(estimated.toVector().subtract(body.toVector()).multiply(0.25));
            Vector player = body.toVector().subtract(eye.toVector()).normalize();
            Vector object = observed.bounds().getCenter().subtract(eye.toVector()).normalize();
            // Balance their screen angles rather than interpolating world distances, which
            // would let a distant object push the player to the edge of the image.
            return eye.clone().add(player.multiply(0.55).add(object.multiply(0.45)).normalize());
        }

        boolean fits(Location eye) {
            Vector direction = aim(eye).toVector().subtract(eye.toVector()).normalize();
            return fits(eye, direction);
        }

        boolean fits(Location eye, Vector direction) {
            for (Vector point : points) {
                Vector ray = point.clone().subtract(eye.toVector());
                if (ray.lengthSquared() < 0.01 || direction.dot(ray.normalize()) < minimumDot) return false;
            }
            return true;
        }

        private static List<Vector> samples(Location body, Location estimated, @Nullable MonitorOperation.Focus observed) {
            if (observed == null) return List.of(body.toVector(), estimated.toVector());
            List<Vector> result = new ArrayList<>();
            corners(result, observed.subjectBounds());
            corners(result, observed.bounds());
            result.add(observed.point().toVector());
            return result;
        }

        private static void corners(List<Vector> result, org.bukkit.util.BoundingBox bounds) {
            for (double x : new double[]{bounds.getMinX(), bounds.getMaxX()}) {
                for (double y : new double[]{bounds.getMinY(), bounds.getMaxY()}) {
                    for (double z : new double[]{bounds.getMinZ(), bounds.getMaxZ()}) result.add(new Vector(x, y, z));
                }
            }
        }
    }

    private static double wrap(double degrees) {
        return degrees - Math.floor((degrees + 180) / 360) * 360;
    }
}
