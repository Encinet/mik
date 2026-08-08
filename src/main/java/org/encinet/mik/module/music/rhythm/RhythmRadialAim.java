package org.encinet.mik.module.music.rhythm;

import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Selects the visible radial cue intersected by the player's view ray. */
final class RhythmRadialAim {
    private static final double MINIMUM_DIRECTION_LENGTH_SQUARED = 1.0E-8;

    private RhythmRadialAim() {
    }

    static Optional<Target> select(Vector eye, Vector viewDirection,
                                   List<Target> targets) {
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
                        Objects.requireNonNull(target, "targets must not contain null")))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(Intersection::normalizedMiss)
                        .thenComparingDouble(Intersection::distance))
                .map(Intersection::target);
    }

    private static Optional<Intersection> intersection(Vector eye, Vector direction,
                                                       Target target) {
        Vector toward = target.center().subtract(eye);
        double distance = direction.dot(toward);
        if (distance <= 0.0) return Optional.empty();
        double perpendicularSquared = Math.max(0.0,
                toward.lengthSquared() - distance * distance);
        double radiusSquared = target.radius() * target.radius();
        if (perpendicularSquared > radiusSquared) return Optional.empty();
        return Optional.of(new Intersection(target,
                perpendicularSquared / radiusSquared, distance));
    }

    private static boolean finite(Vector vector) {
        return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY())
                && Double.isFinite(vector.getZ());
    }

    record Target(RhythmCue cue, Vector center, double radius) {
        Target {
            cue = Objects.requireNonNull(cue, "cue");
            center = Objects.requireNonNull(center, "center").clone();
            if (!finite(center) || !Double.isFinite(radius) || radius <= 0.0) {
                throw new IllegalArgumentException("radial aim target must be finite and positive");
            }
        }

        @Override
        public Vector center() {
            return center.clone();
        }
    }

    private record Intersection(Target target, double normalizedMiss, double distance) {
    }
}
