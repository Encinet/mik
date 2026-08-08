package org.encinet.mik.module.music.rhythm.input;

import org.bukkit.util.Vector;
import org.encinet.mik.module.music.rhythm.RhythmCue;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared ray-to-target selection for mouse-driven world-space rhythm modes. */
public final class RhythmWorldAim {
    private static final double MINIMUM_DIRECTION_LENGTH_SQUARED = 1.0E-8;

    private RhythmWorldAim() {
    }

    public static Optional<Target> select(
            Vector eye, Vector viewDirection, List<Target> targets,
            long judgementPositionMillis) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(viewDirection, "viewDirection");
        Objects.requireNonNull(targets, "targets");
        if (!finite(eye) || !finite(viewDirection)
                || viewDirection.lengthSquared() < MINIMUM_DIRECTION_LENGTH_SQUARED) {
            return Optional.empty();
        }
        Vector direction = viewDirection.clone().normalize();
        return targets.stream()
                .map(target -> intersection(eye, direction,
                        Objects.requireNonNull(target,
                                "targets must not contain null"), judgementPositionMillis))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(Intersection::normalizedMiss)
                        .thenComparingLong(Intersection::absoluteTimingError)
                        .thenComparingLong(value -> value.target().cue().timeMillis())
                        .thenComparingLong(value -> value.target().cue().id())
                        .thenComparingDouble(Intersection::distance))
                .map(Intersection::target);
    }

    public static Vector viewDirection(float yawDegrees, float pitchDegrees) {
        if (!Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("view direction must be finite");
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vector(-Math.sin(yaw) * horizontal,
                -Math.sin(pitch), Math.cos(yaw) * horizontal);
    }

    private static Optional<Intersection> intersection(
            Vector eye, Vector direction, Target target, long judgementPositionMillis) {
        Vector toward = target.center().subtract(eye);
        double distance = direction.dot(toward);
        if (distance <= 0.0) return Optional.empty();
        double perpendicularSquared = Math.max(0.0,
                toward.lengthSquared() - distance * distance);
        double radiusSquared = target.radius() * target.radius();
        if (perpendicularSquared > radiusSquared) return Optional.empty();
        long timingError = absoluteDifference(
                target.cue().timeMillis(), judgementPositionMillis);
        return Optional.of(new Intersection(target,
                perpendicularSquared / radiusSquared, timingError, distance));
    }

    private static long absoluteDifference(long first, long second) {
        long difference;
        try {
            difference = Math.subtractExact(first, second);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
        return difference == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(difference);
    }

    private static boolean finite(Vector vector) {
        return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY())
                && Double.isFinite(vector.getZ());
    }

    public record Target(RhythmCue cue, Vector center, double radius) {
        public Target {
            cue = Objects.requireNonNull(cue, "cue");
            center = Objects.requireNonNull(center, "center").clone();
            if (!finite(center) || !Double.isFinite(radius) || radius <= 0.0) {
                throw new IllegalArgumentException("world aim target must be finite and positive");
            }
        }

        @Override
        public Vector center() {
            return center.clone();
        }
    }

    private record Intersection(Target target, double normalizedMiss,
                                long absoluteTimingError, double distance) {
    }
}
