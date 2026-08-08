package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Optional;

/** Bounded line-of-sight model that keeps spatial cues out of solid terrain. */
public final class RhythmSpatialArena {
    private static final double[] SAMPLE_YAW_OFFSETS = {
            0.0, 45.0, 90.0, 135.0, 180.0, -135.0, -90.0, -45.0
    };
    private static final double[] SAMPLE_PITCHES = {-30.0, 0.0, 38.0};
    private static final double[][] DIRECTION_FALLBACKS = {
            {0.0, 0.0}, {-9.0, 0.0}, {9.0, 0.0},
            {0.0, -8.0}, {0.0, 8.0},
            {-16.0, -8.0}, {16.0, -8.0},
            {-16.0, 8.0}, {16.0, 8.0}
    };

    private final Location eye;
    private final RhythmSpatialProfile profile;
    private final Visibility visibility;
    private final boolean playable;

    public static RhythmSpatialArena inspect(
            Location eye, RhythmSpatialProfile profile) {
        Objects.requireNonNull(eye, "eye");
        World world = Objects.requireNonNull(eye.getWorld(), "eye world");
        return new RhythmSpatialArena(eye, profile,
                (direction, distance, radius) -> clear(
                        world, eye, direction, distance, radius));
    }

    RhythmSpatialArena(Location eye, RhythmSpatialProfile profile,
                       Visibility visibility) {
        this.eye = Objects.requireNonNull(eye, "eye").clone();
        this.profile = Objects.requireNonNull(profile, "profile");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.playable = inspectPlayableVolume();
    }

    public boolean playable() {
        return playable;
    }

    boolean visible(Location target, double radius) {
        Objects.requireNonNull(target, "target");
        Vector offset = target.toVector().subtract(eye.toVector());
        double distance = offset.length();
        return distance > 0.0 && Double.isFinite(distance)
                && Double.isFinite(radius) && radius > 0.0
                && visibility.clear(offset.multiply(1.0 / distance),
                distance, radius);
    }

    Optional<Target> resolve(RhythmSpatialPath.Point requested) {
        Objects.requireNonNull(requested, "requested");
        double[] depths = profile.depths();
        int preferred = nearestDepthIndex(depths, requested.depth());
        for (double[] fallback : DIRECTION_FALLBACKS) {
            Vector direction = RhythmSpatialPath.direction(
                    requested.yawDegrees() + fallback[0],
                    Math.clamp(requested.pitchDegrees() + fallback[1],
                            profile.minimumPitchDegrees(),
                            profile.maximumPitchDegrees()));
            for (int distanceFromPreferred = 0;
                 distanceFromPreferred < depths.length; distanceFromPreferred++) {
                int inward = preferred - distanceFromPreferred;
                if (inward >= 0) {
                    Optional<Target> resolved = resolve(direction, depths[inward]);
                    if (resolved.isPresent()) return resolved;
                }
                int outward = preferred + distanceFromPreferred;
                if (distanceFromPreferred > 0 && outward < depths.length) {
                    Optional<Target> resolved = resolve(direction, depths[outward]);
                    if (resolved.isPresent()) return resolved;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Target> resolve(Vector direction, double depth) {
        double radius = profile.worldHitRadius(depth);
        if (!visibility.clear(direction.clone(), depth, radius)) {
            return Optional.empty();
        }
        return Optional.of(new Target(eye.clone().add(
                direction.clone().multiply(depth)), depth, radius));
    }

    private boolean inspectPlayableVolume() {
        int total = 0;
        int pitchBands = 0;
        boolean[] depthVisible = new boolean[profile.depthCount()];
        for (double pitch : SAMPLE_PITCHES) {
            boolean bandVisible = false;
            for (double yawOffset : SAMPLE_YAW_OFFSETS) {
                Vector direction = RhythmSpatialPath.direction(
                        eye.getYaw() + yawOffset, pitch);
                for (int depthIndex = 0; depthIndex < profile.depthCount(); depthIndex++) {
                    double depth = profile.depth(depthIndex);
                    if (visibility.clear(direction.clone(), depth,
                            profile.worldHitRadius(depth))) {
                        total++;
                        bandVisible = true;
                        depthVisible[depthIndex] = true;
                    }
                }
            }
            if (bandVisible) pitchBands++;
        }
        int visibleDepths = 0;
        for (boolean visible : depthVisible) if (visible) visibleDepths++;
        return total >= 8 && pitchBands >= 2 && visibleDepths >= 2;
    }

    private static boolean clear(World world, Location eye, Vector direction,
                                 double distance, double radius) {
        RayTraceResult collision = world.rayTraceBlocks(eye,
                direction.clone().normalize(), distance + radius,
                FluidCollisionMode.NEVER, true);
        return collision == null;
    }

    private static int nearestDepthIndex(double[] depths, double requested) {
        int selected = 0;
        double difference = Double.POSITIVE_INFINITY;
        for (int index = 0; index < depths.length; index++) {
            double candidate = Math.abs(depths[index] - requested);
            if (candidate < difference) {
                selected = index;
                difference = candidate;
            }
        }
        return selected;
    }

    record Target(Location location, double depth, double hitRadius) {
        Target {
            location = Objects.requireNonNull(location, "location").clone();
            if (!Double.isFinite(depth) || depth <= 0.0
                    || !Double.isFinite(hitRadius) || hitRadius <= 0.0) {
                throw new IllegalArgumentException("spatial arena target must be positive");
            }
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }

    @FunctionalInterface
    interface Visibility {
        boolean clear(Vector direction, double distance, double radius);
    }
}
