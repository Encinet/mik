package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.module.music.rhythm.RhythmCue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministically projects one density-filtered rhythm chart into a bounded
 * spherical path around the player's fixed eye position.
 */
public final class RhythmSpatialPath {
    private static final double MINIMUM_SEPARATION_FACTOR = 1.9;

    private final long seed;
    private final double startingYawDegrees;
    private final RhythmSpatialProfile profile;
    private final Map<Long, AssignedPoint> assigned = new HashMap<>();
    private boolean initialized;
    private long previousTimeMillis;
    private long layer;
    private double yawDegrees;
    private double pitchDegrees;
    private double yawTurnDegrees;
    private double pitchTurnDegrees;
    private double targetYawDegrees;
    private double targetPitchDegrees;
    private int depthIndex;
    private int targetDepthIndex;
    private int cuesUntilNewTarget;

    public RhythmSpatialPath(String trackSeed, double startingYawDegrees,
                             RhythmSpatialProfile profile) {
        this.seed = mix(Objects.requireNonNull(trackSeed, "trackSeed").hashCode());
        if (!Double.isFinite(startingYawDegrees)) {
            throw new IllegalArgumentException("starting yaw must be finite");
        }
        this.startingYawDegrees = startingYawDegrees;
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public Point point(RhythmCue cue) {
        Objects.requireNonNull(cue, "cue");
        AssignedPoint existing = assigned.get(cue.id());
        if (existing != null) return existing.point();

        Point point;
        if (!initialized) {
            yawDegrees = startingYawDegrees;
            pitchDegrees = 0.0;
            targetYawDegrees = yawDegrees;
            targetPitchDegrees = pitchDegrees;
            depthIndex = Math.min(1, profile.depthCount() - 1);
            targetDepthIndex = depthIndex;
            previousTimeMillis = cue.timeMillis();
            initialized = true;
            point = currentPoint();
        } else {
            advance(cue);
            point = currentPoint();
        }
        assigned.put(cue.id(), new AssignedPoint(cue.timeMillis(), point));
        return point;
    }

    public void discardBefore(long timeMillis) {
        long cutoff = Math.max(0L, timeMillis);
        assigned.values().removeIf(value -> value.timeMillis() < cutoff);
    }

    public int retainedPointCount() {
        return assigned.size();
    }

    private void advance(RhythmCue cue) {
        long interval = Math.max(1L, cue.timeMillis() - previousTimeMillis);
        previousTimeMillis = Math.max(previousTimeMillis, cue.timeMillis());
        if (cuesUntilNewTarget <= 0) selectTarget(cue);

        double desiredYaw = Math.clamp(targetYawDegrees - yawDegrees,
                -profile.maximumTurnDegrees(interval),
                profile.maximumTurnDegrees(interval));
        double desiredPitch = Math.clamp(targetPitchDegrees - pitchDegrees,
                -profile.maximumTurnDegrees(interval) * 0.72,
                profile.maximumTurnDegrees(interval) * 0.72);
        yawTurnDegrees = approach(yawTurnDegrees, desiredYaw,
                profile.maximumTurnAccelerationDegrees());
        pitchTurnDegrees = approach(pitchTurnDegrees, desiredPitch,
                profile.maximumTurnAccelerationDegrees() * 0.72);

        double nextYaw = yawDegrees + yawTurnDegrees;
        double nextPitch = Math.clamp(pitchDegrees + pitchTurnDegrees,
                profile.minimumPitchDegrees(), profile.maximumPitchDegrees());
        double maximumTurn = profile.maximumTurnDegrees(interval);
        double actualTurn = angularDistance(yawDegrees, pitchDegrees,
                nextYaw, nextPitch);
        if (actualTurn > maximumTurn) {
            double scale = maximumTurn / actualTurn;
            nextYaw = yawDegrees + yawTurnDegrees * scale;
            nextPitch = Math.clamp(pitchDegrees + pitchTurnDegrees * scale,
                    profile.minimumPitchDegrees(), profile.maximumPitchDegrees());
            yawTurnDegrees *= scale;
            pitchTurnDegrees *= scale;
        }

        double minimumSeparation = profile.aimRadiusDegrees()
                * MINIMUM_SEPARATION_FACTOR;
        double separation = angularDistance(yawDegrees, pitchDegrees,
                nextYaw, nextPitch);
        if (separation < minimumSeparation && maximumTurn >= minimumSeparation) {
            long mixed = mix(seed ^ cue.signature() ^ cue.id());
            double sign = (mixed & 1L) == 0L ? -1.0 : 1.0;
            double yawNudge = sign * Math.sqrt(Math.max(0.0,
                    minimumSeparation * minimumSeparation
                            - Math.pow(nextPitch - pitchDegrees, 2.0)));
            nextYaw = yawDegrees + yawNudge;
            yawTurnDegrees = yawNudge;
        }

        actualTurn = angularDistance(yawDegrees, pitchDegrees,
                nextYaw, nextPitch);
        if (actualTurn > maximumTurn) {
            Step limited = limitStep(yawDegrees, pitchDegrees,
                    nextYaw, nextPitch, maximumTurn);
            nextYaw = limited.yawDegrees();
            nextPitch = limited.pitchDegrees();
            yawTurnDegrees = nextYaw - yawDegrees;
            pitchTurnDegrees = nextPitch - pitchDegrees;
        }

        yawDegrees = clampYawExcursion(nextYaw);
        pitchDegrees = nextPitch;
        if (depthIndex < targetDepthIndex) depthIndex++;
        else if (depthIndex > targetDepthIndex) depthIndex--;
        cuesUntilNewTarget--;
    }

    private void selectTarget(RhythmCue cue) {
        long value = mix(seed ^ cue.signature()
                ^ Long.rotateLeft(cue.id(), 17)
                ^ (++layer * 0x9E3779B97F4A7C15L));
        double randomYaw = signedUnit(value) * 105.0;
        double musicalYaw = cue.stereoBalance() * 42.0;
        double proposedYaw = yawDegrees + randomYaw + musicalYaw;
        double excursion = proposedYaw - startingYawDegrees;
        double maximumExcursion = profile.maximumYawExcursionDegrees();
        if (Math.abs(excursion) > maximumExcursion) {
            proposedYaw = startingYawDegrees
                    - Math.copySign(maximumExcursion * 0.58, excursion);
        }
        targetYawDegrees = clampYawExcursion(proposedYaw);

        long pitchValue = mix(value ^ 0xD1B54A32D192ED03L);
        double pitchRange = profile.maximumPitchDegrees()
                - profile.minimumPitchDegrees();
        double randomPitch = profile.minimumPitchDegrees()
                + unit(pitchValue) * pitchRange;
        double musicalPitch = cue.toneBalance() * pitchRange * 0.24;
        targetPitchDegrees = Math.clamp(randomPitch * 0.72 + musicalPitch,
                profile.minimumPitchDegrees(), profile.maximumPitchDegrees());

        if (cue.strength() >= 0.82) {
            targetDepthIndex = 0;
        } else {
            targetDepthIndex = Math.floorMod((int) (value >>> 32),
                    profile.depthCount());
        }
        cuesUntilNewTarget = 3 + Math.floorMod((int) (pitchValue >>> 32), 4);
    }

    private double clampYawExcursion(double yaw) {
        return startingYawDegrees + Math.clamp(yaw - startingYawDegrees,
                -profile.maximumYawExcursionDegrees(),
                profile.maximumYawExcursionDegrees());
    }

    private Point currentPoint() {
        return new Point(yawDegrees, pitchDegrees, profile.depth(depthIndex));
    }

    private static double approach(double current, double target, double maximumDelta) {
        return current + Math.clamp(target - current, -maximumDelta, maximumDelta);
    }

    static double angularDistance(double firstYaw, double firstPitch,
                                  double secondYaw, double secondPitch) {
        Vector first = direction(firstYaw, firstPitch);
        Vector second = direction(secondYaw, secondPitch);
        double dot = Math.clamp(first.dot(second), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static Step limitStep(double fromYaw, double fromPitch,
                                  double toYaw, double toPitch, double maximumTurn) {
        double low = 0.0;
        double high = 1.0;
        for (int iteration = 0; iteration < 48; iteration++) {
            double middle = (low + high) * 0.5;
            double yaw = fromYaw + (toYaw - fromYaw) * middle;
            double pitch = fromPitch + (toPitch - fromPitch) * middle;
            if (angularDistance(fromYaw, fromPitch, yaw, pitch) <= maximumTurn) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return new Step(fromYaw + (toYaw - fromYaw) * low,
                fromPitch + (toPitch - fromPitch) * low);
    }

    public static Vector direction(double yawDegrees, double pitchDegrees) {
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("spatial direction must be finite");
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vector(-Math.sin(yaw) * horizontal,
                Math.sin(pitch), Math.cos(yaw) * horizontal);
    }

    private static double signedUnit(long value) {
        return unit(value) * 2.0 - 1.0;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    public record Point(double yawDegrees, double pitchDegrees, double depth) {
        public Point {
            if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)
                    || !Double.isFinite(depth) || depth <= 0.0) {
                throw new IllegalArgumentException("spatial point must be finite and positive");
            }
        }

        Vector direction() {
            return RhythmSpatialPath.direction(yawDegrees, pitchDegrees);
        }

        Location worldPoint(Location eye) {
            return Objects.requireNonNull(eye, "eye").clone()
                    .add(direction().multiply(depth));
        }
    }

    private record AssignedPoint(long timeMillis, Point point) {
    }

    private record Step(double yawDegrees, double pitchDegrees) {
    }
}
