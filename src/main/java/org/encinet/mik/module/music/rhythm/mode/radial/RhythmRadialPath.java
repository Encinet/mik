package org.encinet.mik.module.music.rhythm.mode.radial;

import org.bukkit.Location;
import org.encinet.mik.module.music.rhythm.RhythmCue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a stable, progressively turning approach direction for radial cues.
 * Both angular speed and angular acceleration are bounded, so a new direction
 * is disclosed over several cues instead of snapping across the scene.
 */
public final class RhythmRadialPath {
    static final double MAXIMUM_TURN_DEGREES = 28.0;
    static final double MAXIMUM_TURN_ACCELERATION_DEGREES = 8.0;
    static final double SPAWN_RADIUS = 4.60;
    static final double HIT_RADIUS = 1.15;
    static final double CUE_HEIGHT = 1.34;
    private static final double VERTICAL_WAVE = 0.24;
    private static final double LATE_CENTER_PROGRESS = 1.08;

    private final long seed;
    private final double startingAngleDegrees;
    private final Map<Long, AssignedAngle> assignedAngles = new HashMap<>();
    private double angleDegrees;
    private double targetDegrees;
    private double turnDegrees;
    private int cuesUntilNewTarget;
    private long layer;
    private boolean initialized;

    public RhythmRadialPath(String seed, double startingAngleDegrees) {
        this.seed = mix(Objects.requireNonNull(seed, "seed").hashCode());
        if (!Double.isFinite(startingAngleDegrees)) {
            throw new IllegalArgumentException("starting angle must be finite");
        }
        this.startingAngleDegrees = normalize(startingAngleDegrees);
    }

    public double angleDegrees(RhythmCue cue) {
        Objects.requireNonNull(cue, "cue");
        AssignedAngle existing = assignedAngles.get(cue.id());
        if (existing != null) return existing.angleDegrees();

        if (!initialized) {
            // The first cue is deliberately straight ahead. Subsequent seeded
            // targets still cover the full circle, but are reached gradually.
            angleDegrees = startingAngleDegrees;
            targetDegrees = angleDegrees;
            cuesUntilNewTarget = 0;
            initialized = true;
        } else {
            advance(cue);
        }
        assignedAngles.put(cue.id(), new AssignedAngle(
                cue.timeMillis(), angleDegrees));
        return angleDegrees;
    }

    public void discardBefore(long timeMillis) {
        long cutoff = Math.max(0L, timeMillis);
        assignedAngles.values().removeIf(value -> value.timeMillis() < cutoff);
    }

    public int retainedAngleCount() {
        return assignedAngles.size();
    }

    private void advance(RhythmCue cue) {
        if (cuesUntilNewTarget <= 0) {
            long value = mix(seed ^ cue.id() ^ Long.rotateLeft(cue.timeMillis(), 23)
                    ^ (++layer * 0x9E3779B97F4A7C15L));
            targetDegrees = unitAngle(value);
            cuesUntilNewTarget = 3 + Math.floorMod((int) (value >>> 32), 4);
        }

        double desiredTurn = Math.clamp(shortestDelta(angleDegrees, targetDegrees),
                -MAXIMUM_TURN_DEGREES, MAXIMUM_TURN_DEGREES);
        double acceleration = Math.clamp(desiredTurn - turnDegrees,
                -MAXIMUM_TURN_ACCELERATION_DEGREES,
                MAXIMUM_TURN_ACCELERATION_DEGREES);
        turnDegrees = Math.clamp(turnDegrees + acceleration,
                -MAXIMUM_TURN_DEGREES, MAXIMUM_TURN_DEGREES);
        angleDegrees = normalize(angleDegrees + turnDegrees);
        cuesUntilNewTarget--;
    }

    /** Absolute world position of a cue approaching the player's body. */
    public static Location point(Location anchor, double angleDegrees,
                                 double progress) {
        Objects.requireNonNull(anchor, "anchor");
        if (!Double.isFinite(angleDegrees) || !Double.isFinite(progress)) {
            throw new IllegalArgumentException("radial cue coordinates must be finite");
        }
        double radians = Math.toRadians(angleDegrees);
        double radius = approachRadius(progress);
        return anchor.clone().add(
                Math.cos(radians) * radius,
                CUE_HEIGHT + Math.sin(radians * 2.0) * VERTICAL_WAVE,
                Math.sin(radians) * radius);
    }

    /** Converts Bukkit yaw into this path's X/Z azimuth convention. */
    public static double facingAngle(float yawDegrees) {
        return normalize(90.0 + yawDegrees);
    }

    private static double approachRadius(double progress) {
        if (progress <= 1.0) {
            return SPAWN_RADIUS + progress * (HIT_RADIUS - SPAWN_RADIUS);
        }
        double late = (progress - 1.0) / (LATE_CENTER_PROGRESS - 1.0);
        return HIT_RADIUS * (1.0 - late);
    }

    static double shortestDelta(double fromDegrees, double toDegrees) {
        double delta = normalize(toDegrees) - normalize(fromDegrees);
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        return delta;
    }

    private static double unitAngle(long value) {
        return (value >>> 11) * 0x1.0p-53 * 360.0;
    }

    private static double normalize(double value) {
        double normalized = value % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private record AssignedAngle(long timeMillis, double angleDegrees) {
    }
}
