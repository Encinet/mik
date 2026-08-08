package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.encinet.mik.module.music.rhythm.RhythmDifficulty;

import java.util.Objects;

/** Difficulty-specific readability and reach policy for the spatial aim mode. */
public record RhythmSpatialProfile(
        long approachMillis, double aimRadiusDegrees,
        double minimumPitchDegrees, double maximumPitchDegrees,
        double maximumYawExcursionDegrees,
        double baseTurnDegrees, double turnDegreesPerSecond,
        double maximumTurnAccelerationDegrees, double[] depths) {
    private static final double[] EASY_DEPTHS = {2.4, 3.5};
    private static final double[] FULL_DEPTHS = {2.4, 3.5, 4.6};

    public RhythmSpatialProfile {
        if (approachMillis < 1L || !positiveFinite(aimRadiusDegrees)
                || !Double.isFinite(minimumPitchDegrees)
                || !Double.isFinite(maximumPitchDegrees)
                || minimumPitchDegrees >= maximumPitchDegrees
                || !positiveFinite(maximumYawExcursionDegrees)
                || !positiveFinite(baseTurnDegrees)
                || !positiveFinite(turnDegreesPerSecond)
                || !positiveFinite(maximumTurnAccelerationDegrees)) {
            throw new IllegalArgumentException("invalid spatial rhythm profile");
        }
        depths = Objects.requireNonNull(depths, "depths").clone();
        if (depths.length < 2) {
            throw new IllegalArgumentException("spatial rhythm needs at least two depth layers");
        }
        double previous = 0.0;
        for (double depth : depths) {
            if (!positiveFinite(depth) || depth <= previous) {
                throw new IllegalArgumentException("spatial depths must be positive and increasing");
            }
            previous = depth;
        }
    }

    public static RhythmSpatialProfile forDifficulty(
            RhythmDifficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> new RhythmSpatialProfile(2_400L, 7.0,
                    -28.0, 42.0, 75.0, 10.0, 38.0, 7.0, EASY_DEPTHS);
            case NORMAL -> new RhythmSpatialProfile(2_100L, 6.0,
                    -44.0, 58.0, 240.0, 11.0, 52.0, 9.0, FULL_DEPTHS);
            case HARD -> new RhythmSpatialProfile(1_800L, 5.0,
                    -50.0, 65.0, 250.0, 12.0, 66.0, 11.0, FULL_DEPTHS);
            case EXPERT -> new RhythmSpatialProfile(1_600L, 4.5,
                    -52.0, 68.0, 260.0, 13.0, 80.0, 13.0, FULL_DEPTHS);
        };
    }

    @Override
    public double[] depths() {
        return depths.clone();
    }

    int depthCount() {
        return depths.length;
    }

    double depth(int index) {
        return depths[Math.clamp(index, 0, depths.length - 1)];
    }

    double maximumTurnDegrees(long intervalMillis) {
        double seconds = Math.clamp(intervalMillis / 1_000.0, 0.05, 1.5);
        return Math.min(80.0, baseTurnDegrees + turnDegreesPerSecond * seconds);
    }

    public double worldHitRadius(double depth) {
        if (!positiveFinite(depth)) throw new IllegalArgumentException("depth must be positive");
        return depth * Math.tan(Math.toRadians(aimRadiusDegrees));
    }

    public int maximumVisibleCues(RhythmDifficulty difficulty) {
        return (int) Math.ceil(Objects.requireNonNull(difficulty, "difficulty")
                .maximumCuesPerSecond() * approachMillis / 1_000.0) + 2;
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
