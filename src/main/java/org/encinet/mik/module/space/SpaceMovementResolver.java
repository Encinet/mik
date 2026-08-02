package org.encinet.mik.module.space;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Resolves the post-seam movement segment against destination-side collisions. */
final class SpaceMovementResolver {

    private static final double MAX_SEGMENT_LENGTH = 32.0;
    private static final double SAMPLE_STEP = 0.125;
    private static final double ENTRY_CLEARANCE_SAMPLE_STEP = 0.03125;
    private static final double MOVEMENT_EPSILON = 1.0E-7;
    private static final int BINARY_SEARCH_STEPS = 20;

    private SpaceMovementResolver() {
    }

    /**
     * Uses vanilla's axis order: vertical first, then the larger horizontal component
     * before the smaller one. Sampling prevents a final
     * point below a thin shape from skipping the collision interval entirely.
     */
    static Optional<Result> resolve(
            SpaceVector entry,
            SpaceVector desired,
            SpaceVector exitDirection,
            double entryClearance,
            double maxStepHeight,
            Predicate<SpaceVector> collides
    ) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(exitDirection, "exitDirection");
        Objects.requireNonNull(collides, "collides");
        if (!Double.isFinite(entryClearance) || entryClearance < 0.0) {
            throw new IllegalArgumentException("entryClearance must be finite and non-negative");
        }
        if (!Double.isFinite(maxStepHeight) || maxStepHeight < 0.0) {
            throw new IllegalArgumentException("maxStepHeight must be finite and non-negative");
        }
        SpaceVector normalizedExit = exitDirection.normalized();
        SpaceVector displacement = desired.subtract(entry);
        if (displacement.length() > MAX_SEGMENT_LENGTH) {
            return Optional.empty();
        }

        Optional<EntryResolution> safeEntry = resolveEntryCollision(
                entry, normalizedExit, entryClearance, maxStepHeight, collides);
        if (safeEntry.isEmpty()) {
            return Optional.empty();
        }
        SpaceVector position = safeEntry.get().position();
        AxisMovement vertical = sweep(position, Axis.Y, displacement.y(), collides);
        position = vertical.position();

        AxisMovement firstHorizontal;
        AxisMovement secondHorizontal;
        if (Math.abs(displacement.x()) < Math.abs(displacement.z())) {
            firstHorizontal = sweep(position, Axis.Z, displacement.z(), collides);
            position = firstHorizontal.position();
            secondHorizontal = sweep(position, Axis.X, displacement.x(), collides);
        } else {
            firstHorizontal = sweep(position, Axis.X, displacement.x(), collides);
            position = firstHorizontal.position();
            secondHorizontal = sweep(position, Axis.Z, displacement.z(), collides);
        }
        position = secondHorizontal.position();

        boolean blockedX = axisBlocked(Axis.X, firstHorizontal, secondHorizontal);
        boolean blockedZ = axisBlocked(Axis.Z, firstHorizontal, secondHorizontal);
        return Optional.of(new Result(
                position, blockedX,
                safeEntry.get().blockedY() || vertical.blocked(), blockedZ));
    }

    private static Optional<EntryResolution> resolveEntryCollision(
            SpaceVector entry,
            SpaceVector exitDirection,
            double entryClearance,
            double maxStepHeight,
            Predicate<SpaceVector> collides
    ) {
        if (!collides.test(entry)) {
            return Optional.of(new EntryResolution(entry, false));
        }

        // The Bukkit location is an entity anchor (usually its feet), not the
        // complete collision shape. At a floor/ceiling exit the anchor can be on
        // the portal plane while most of the upright entity is still inside the
        // backing blocks. Move only far enough along the exit normal for the root
        // collision box to pass the plane. This is portal clearance, not a blocked
        // velocity axis.
        if (entryClearance > MOVEMENT_EPSILON) {
            Optional<SpaceVector> throughPlane = nearestClearPosition(
                    entry, exitDirection, entryClearance, collides);
            if (throughPlane.isPresent()) {
                return Optional.of(new EntryResolution(throughPlane.get(), false));
            }
        }

        // A vertical step is useful at a wall exit whose feet slightly overlap a
        // slab or stair. It must not move an up/down exit back to the wrong side.
        if (Math.abs(exitDirection.y()) > 1.0 - MOVEMENT_EPSILON) {
            return Optional.empty();
        }
        if (maxStepHeight <= MOVEMENT_EPSILON) {
            return Optional.empty();
        }
        Optional<SpaceVector> above = nearestClearPosition(
                entry, new SpaceVector(0.0, 1.0, 0.0), maxStepHeight, collides);
        Optional<SpaceVector> below = nearestClearPosition(
                entry, new SpaceVector(0.0, -1.0, 0.0), maxStepHeight, collides);
        if (above.isEmpty()) {
            return below.map(position -> new EntryResolution(position, true));
        }
        if (below.isEmpty()) {
            return above.map(position -> new EntryResolution(position, true));
        }
        double upwardDistance = above.get().y() - entry.y();
        double downwardDistance = entry.y() - below.get().y();
        SpaceVector nearest = upwardDistance <= downwardDistance
                ? above.get() : below.get();
        return Optional.of(new EntryResolution(nearest, true));
    }

    private static Optional<SpaceVector> nearestClearPosition(
            SpaceVector entry,
            SpaceVector direction,
            double maximumDistance,
            Predicate<SpaceVector> collides
    ) {
        int samples = Math.max(1,
                (int) Math.ceil(maximumDistance / ENTRY_CLEARANCE_SAMPLE_STEP));
        double lastCollidingDistance = 0.0;
        for (int sample = 1; sample <= samples; sample++) {
            double distance = maximumDistance * sample / samples;
            SpaceVector candidate = entry.add(direction.multiply(distance));
            if (collides.test(candidate)) {
                lastCollidingDistance = distance;
                continue;
            }
            double low = lastCollidingDistance;
            double high = distance;
            for (int iteration = 0; iteration < BINARY_SEARCH_STEPS; iteration++) {
                double middle = (low + high) * 0.5;
                if (collides.test(entry.add(direction.multiply(middle)))) {
                    low = middle;
                } else {
                    high = middle;
                }
            }
            return Optional.of(entry.add(direction.multiply(high)));
        }
        return Optional.empty();
    }

    private static boolean axisBlocked(
            Axis axis,
            AxisMovement first,
            AxisMovement second
    ) {
        return first.axis() == axis ? first.blocked() : second.blocked();
    }

    private static AxisMovement sweep(
            SpaceVector start,
            Axis axis,
            double distance,
            Predicate<SpaceVector> collides
    ) {
        if (Math.abs(distance) <= MOVEMENT_EPSILON) {
            return new AxisMovement(axis, start, false);
        }
        int samples = Math.max(1, (int) Math.ceil(Math.abs(distance) / SAMPLE_STEP));
        double lastSafeProgress = 0.0;
        for (int sample = 1; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            if (!collides.test(move(start, axis, distance * progress))) {
                lastSafeProgress = progress;
                continue;
            }
            double low = lastSafeProgress;
            double high = progress;
            for (int iteration = 0; iteration < BINARY_SEARCH_STEPS; iteration++) {
                double middle = (low + high) * 0.5;
                if (collides.test(move(start, axis, distance * middle))) {
                    high = middle;
                } else {
                    low = middle;
                }
            }
            return new AxisMovement(axis, move(start, axis, distance * low), true);
        }
        return new AxisMovement(axis, move(start, axis, distance), false);
    }

    private static SpaceVector move(SpaceVector start, Axis axis, double distance) {
        return switch (axis) {
            case X -> new SpaceVector(start.x() + distance, start.y(), start.z());
            case Y -> new SpaceVector(start.x(), start.y() + distance, start.z());
            case Z -> new SpaceVector(start.x(), start.y(), start.z() + distance);
        };
    }

    record Result(
            SpaceVector position,
            boolean blockedX,
            boolean blockedY,
            boolean blockedZ
    ) {

        SpaceVector clipVelocity(SpaceVector velocity) {
            return new SpaceVector(
                    blockedX ? 0.0 : velocity.x(),
                    blockedY ? 0.0 : velocity.y(),
                    blockedZ ? 0.0 : velocity.z());
        }
    }

    private record AxisMovement(Axis axis, SpaceVector position, boolean blocked) {
    }

    private record EntryResolution(SpaceVector position, boolean blockedY) {
    }

    private enum Axis {
        X,
        Y,
        Z
    }
}
