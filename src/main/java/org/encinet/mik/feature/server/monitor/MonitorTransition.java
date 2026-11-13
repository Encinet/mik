package org.encinet.mik.feature.server.monitor;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Independent inertia for subject translation, local camera placement and aim. */
final class MonitorTransition {
    private static final double FOLLOW_DAMPING = 0.5;
    private static final double MAX_FOLLOW_LAG = 0.75;
    private static final double ORBIT_SPEED = 6;
    private static final double AIM_SPEED = 8;

    private final Vector lag = new Vector();
    private final Vector velocity = new Vector();
    private final Spring orbit = new Spring();
    private final Spring radius = new Spring();
    private final Spring height = new Spring();
    private final Spring yaw = new Spring();
    private final Spring pitch = new Spring();
    private int orbitDirection;
    private double previousAngle = Double.NaN;

    void reset() {
        lag.zero();
        velocity.zero();
        orbit.velocity = 0;
        radius.velocity = 0;
        height.velocity = 0;
        yaw.velocity = 0;
        pitch.velocity = 0;
        orbitDirection = 0;
        previousAngle = Double.NaN;
    }

    @Nullable Location position(Location current, Location target, Vector movement, Location desired, boolean retracting,
                                BiPredicate<Location, Location> clearSegment) {
        // Recover the actual local rig before advancing the subject-follow spring. Capping
        // translation lag must never jump most of the way to a different camera angle.
        Vector offset = current.toVector().subtract(target.toVector()).add(movement).subtract(lag);
        Vector destination = desired.toVector().subtract(target.toVector());
        double angle = Math.toDegrees(Math.atan2(offset.getX(), -offset.getZ()));
        double goal = Math.toDegrees(Math.atan2(destination.getX(), -destination.getZ()));
        double turn = wrap(goal - angle);
        if (Math.abs(turn) < 20 || (!Double.isNaN(previousAngle) && Math.abs(wrap(goal - previousAngle)) > 45)) {
            orbitDirection = 0;
        }
        previousAngle = goal;
        // At the opposite side, numerical noise must not reverse an orbit already underway.
        if (orbitDirection != 0 && turn * orbitDirection < 0) turn += orbitDirection * 360;
        if (orbitDirection == 0 && Math.abs(turn) > 25) {
            if (Math.abs(turn) > 160 && Math.abs(orbit.velocity) > 0.1 && turn * orbit.velocity < 0) {
                turn += Math.copySign(360, orbit.velocity);
            }
            if (!clearArc(target, offset, destination, angle, turn, clearSegment)) {
                turn -= Math.copySign(360, turn);
                if (!clearArc(target, offset, destination, angle, turn, clearSegment)) return null;
            }
            orbitDirection = turn > 0 ? 1 : -1;
        }
        double nextAngle = Math.toRadians(orbit.advance(angle, angle + turn, 0.35, ORBIT_SPEED));
        double damping = retracting ? FOLLOW_DAMPING * 1.1 : FOLLOW_DAMPING;
        double reach = radius.advance(Math.hypot(offset.getX(), offset.getZ()),
                Math.hypot(destination.getX(), destination.getZ()), retracting ? 0.85 : damping, retracting ? 0.9 : 0.65);
        double rise = height.advance(offset.getY(), destination.getY(), damping, 0.35);

        Vector error = lag.clone().subtract(movement);
        limit(error, MAX_FOLLOW_LAG);
        Vector step = velocity.clone().add(error.clone().multiply(damping));
        double decay = Math.exp(-damping);
        lag.copy(error.add(step).multiply(decay));
        limit(lag, MAX_FOLLOW_LAG);
        velocity.subtract(step.multiply(damping)).multiply(decay);
        return target.clone().add(Math.sin(nextAngle) * reach, rise, -Math.cos(nextAngle) * reach).add(lag);
    }

    private boolean clearArc(Location target, Vector from, Vector to, double angle, double turn,
                             BiPredicate<Location, Location> clearSegment) {
        int steps = Math.max(1, (int) Math.ceil(Math.abs(turn) / 15));
        double fromRadius = Math.hypot(from.getX(), from.getZ());
        double toRadius = Math.hypot(to.getX(), to.getZ());
        Location previous = target.clone().add(from).add(lag);
        for (int step = 1; step <= steps; step++) {
            double fraction = (double) step / steps;
            double direction = Math.toRadians(angle + turn * fraction);
            double reach = fromRadius + (toRadius - fromRadius) * fraction;
            double rise = from.getY() + (to.getY() - from.getY()) * fraction;
            Location next = target.clone().add(Math.sin(direction) * reach, rise, -Math.cos(direction) * reach)
                    .add(lag.clone().multiply(1 - fraction));
            if (!clearSegment.test(previous, next)) return false;
            previous = next;
        }
        return true;
    }

    /** Damp pan/tilt, then release only as much damping as clear framing requires. */
    void aim(Location current, Location desired, Predicate<Vector> fits) {
        double idealYaw = current.getYaw() + wrap(desired.getYaw() - current.getYaw());
        double idealPitch = desired.getPitch();
        double easedYaw = yaw.advance(current.getYaw(), idealYaw, 0.65, AIM_SPEED);
        double easedPitch = Math.clamp(pitch.advance(current.getPitch(), idealPitch, 0.65, AIM_SPEED), -90, 90);
        desired.setYaw((float) easedYaw);
        desired.setPitch((float) easedPitch);
        if (fits.test(desired.getDirection())) return;

        double low = 0;
        double high = 1;
        for (int step = 0; step < 8; step++) {
            double fraction = (low + high) / 2;
            desired.setYaw((float) (easedYaw + (idealYaw - easedYaw) * fraction));
            desired.setPitch((float) (easedPitch + (idealPitch - easedPitch) * fraction));
            if (fits.test(desired.getDirection())) high = fraction;
            else low = fraction;
        }
        desired.setYaw((float) (easedYaw + (idealYaw - easedYaw) * high));
        desired.setPitch((float) (easedPitch + (idealPitch - easedPitch) * high));
        yaw.velocity = Math.clamp(desired.getYaw() - current.getYaw(), -AIM_SPEED, AIM_SPEED);
        pitch.velocity = Math.clamp(desired.getPitch() - current.getPitch(), -AIM_SPEED, AIM_SPEED);
    }

    private static void limit(Vector vector, double maximum) {
        if (vector.lengthSquared() > maximum * maximum) vector.normalize().multiply(maximum);
    }

    private static double wrap(double degrees) {
        return degrees - Math.floor((degrees + 180) / 360) * 360;
    }

    private static final class Spring {
        double velocity;

        double advance(double current, double desired, double damping, double maximumSpeed) {
            // Integrate a critically damped spring for one tick. A distant goal becomes
            // a nearby moving goal, bounding speed without discarding the remaining trip.
            double error = Math.clamp(current - desired, -2 * maximumSpeed / damping, 2 * maximumSpeed / damping);
            double step = velocity + error * damping;
            double decay = Math.exp(-damping);
            double displacement = (error + step) * decay - error;
            velocity = Math.clamp((velocity - step * damping) * decay, -maximumSpeed, maximumSpeed);
            return current + Math.clamp(displacement, -maximumSpeed, maximumSpeed);
        }
    }
}
