package org.encinet.mik.module.afk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded, multi-window movement analysis for reward eligibility, not human detection.
 *
 * <p>Two complete five-minute windows and the latest minute must corroborate a
 * pattern. Direction entropy alone cannot distinguish a fixed loop from random
 * choices; conditional entropy, lag agreement, three-dimensional extent and
 * vertical reversals provide complementary descriptions. Small circles and jumps
 * remain legal movement. A recommendation only restricts activity rewards after
 * ten minutes without accepted intentional gameplay; it never creates AFK status.
 *
 * <p>Samples are throttled to 500 ms, expire after ten minutes, and analyses are
 * cached for five seconds. Missing intervals, world changes and non-finite input
 * break continuity instead of fabricating movement. Adapters must call this from
 * a periodic sampler, not solely from move events, to include stationary periods.
 */
final class AfkBehaviorAnalyzer {

    static final long SAMPLE_INTERVAL_MILLIS = 500L;
    static final long WINDOW_MILLIS = 5L * 60L * 1_000L;
    static final long ANALYSIS_MILLIS = 2L * WINDOW_MILLIS;
    static final long SHORT_WINDOW_MILLIS = 60_000L;
    private static final long ANALYSIS_INTERVAL_MILLIS = 5_000L;
    private static final long MAX_SAMPLE_GAP_MILLIS = 3L * SAMPLE_INTERVAL_MILLIS;
    private static final double MAX_LOCAL_RANGE = 24.0D;
    private static final double MAX_LOCAL_VERTICAL_RANGE = 8.0D;
    private static final double MIN_LOCAL_DISTANCE = 32.0D;
    static final int MOVEMENT_DIRECTION_BUCKETS = 4;
    static final int MIN_SAMPLES_PER_WINDOW = 360;
    static final double MIN_MOVING_SAMPLE_RATIO = 0.50D;
    static final int MIN_MOVEMENT_DIRECTION_SAMPLES = 180;
    static final int MIN_OCCUPIED_MOVEMENT_DIRECTION_BUCKETS = MOVEMENT_DIRECTION_BUCKETS;
    static final double MIN_NORMALIZED_MOVEMENT_DIRECTION_ENTROPY = 0.985D;
    static final double MAX_MOVEMENT_DIRECTION_BUCKET_COEFFICIENT_OF_VARIATION = 0.25D;
    static final double MIN_NORMALIZED_MOVEMENT_DISTANCE_ENTROPY = 0.980D;
    static final double MAX_MOVEMENT_DISTANCE_BUCKET_COEFFICIENT_OF_VARIATION = 0.30D;
    static final double MAX_NET_MOVEMENT_RATIO = 0.20D;
    static final int MIN_LARGE_MOVEMENT_DIRECTION_TURNS = 24;
    static final float LARGE_MOVEMENT_DIRECTION_TURN_DEGREES = 30.0F;

    private final Deque<Sample> samples = new ArrayDeque<>();
    private long lastSampleAt = Long.MIN_VALUE;
    private UUID lastWorldId;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean hasLastPosition;
    private long lastAnalysisAt = Long.MIN_VALUE;
    private Analysis cachedAnalysis;

    void record(
            UUID worldId,
            double x,
            double y,
            double z,
            float ignoredYaw,
            boolean movementInputActive,
            long now
    ) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            reset();
            return;
        }
        if (hasLastPosition && !Objects.equals(lastWorldId, worldId)) {
            reset();
        }
        if (lastSampleAt != Long.MIN_VALUE
                && elapsed(now, lastSampleAt) < SAMPLE_INTERVAL_MILLIS) {
            return;
        }

        boolean sameWorld = hasLastPosition && Objects.equals(lastWorldId, worldId)
                && lastSampleAt != Long.MIN_VALUE
                && elapsed(now, lastSampleAt) <= MAX_SAMPLE_GAP_MILLIS;
        double deltaX = sameWorld ? x - lastX : 0.0D;
        double deltaY = sameWorld ? y - lastY : 0.0D;
        double deltaZ = sameWorld ? z - lastZ : 0.0D;
        double horizontalDistance = Math.hypot(deltaX, deltaZ);
        boolean horizontalMovement = sameWorld && movementInputActive && horizontalDistance >= 0.1D;
        float movementDirection = horizontalMovement
                ? normalizeYaw((float) Math.toDegrees(Math.atan2(deltaZ, deltaX)))
                : 0.0F;
        samples.addLast(new Sample(
                now,
                horizontalMovement,
                horizontalMovement ? movementDirectionBucket(movementDirection) : -1,
                movementDirection,
                deltaX,
                deltaZ,
                horizontalDistance,
                x, y, z, deltaY,
                sameWorld && movementInputActive && Math.abs(deltaY) >= 0.1D
        ));
        lastSampleAt = now;
        lastWorldId = worldId;
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLastPosition = true;
        removeExpired(now);
    }

    /**
     * Historical method name for a reward-only policy recommendation. Requires a
     * full intentional-activity grace period; never certifies automation or AFK.
     */
    boolean isLikelyAutomated(long now, long lastIntentionalActivityAt) {
        if (elapsed(now, lastIntentionalActivityAt) < ANALYSIS_MILLIS) {
            return false;
        }

        return analyze(now).rewardRestrictionRecommended();
    }

    /**
     * Returns explainable window metrics, including an explicit insufficient-data
     * result. Entropies and the recommendation are not probabilities of cheating.
     * Caller-side intentional-activity age remains a separate prerequisite.
     */
    Analysis analyze(long now) {
        if (cachedAnalysis != null && elapsed(now, lastAnalysisAt) < ANALYSIS_INTERVAL_MILLIS) {
            return cachedAnalysis;
        }
        removeExpired(now);
        long recentWindowStart = now - WINDOW_MILLIS;
        long previousWindowStart = now - ANALYSIS_MILLIS;
        Window previous = summarize(previousWindowStart, recentWindowStart);
        Window recent = summarize(recentWindowStart, now + 1L);
        Window shortWindow = summarize(now - SHORT_WINDOW_MILLIS, now + 1L);
        boolean sufficient = previous.statistics.hasSufficientCoverage(WINDOW_MILLIS)
                && recent.statistics.hasSufficientCoverage(WINDOW_MILLIS)
                && shortWindow.statistics.hasSufficientCoverage(SHORT_WINDOW_MILLIS);
        Pattern pattern = sufficient ? corroboratedPattern(previous, recent, shortWindow)
                : Pattern.INSUFFICIENT_DATA;
        cachedAnalysis = new Analysis(pattern, previous.statistics,
                recent.statistics, shortWindow.statistics);
        lastAnalysisAt = now;
        return cachedAnalysis;
    }

    void reset() {
        samples.clear();
        lastSampleAt = Long.MIN_VALUE;
        lastWorldId = null;
        hasLastPosition = false;
        lastAnalysisAt = Long.MIN_VALUE;
        cachedAnalysis = null;
    }

    void resumeAfter(long pausedFor, UUID worldId, double x, double y, double z) {
        if (hasLastPosition && !Objects.equals(lastWorldId, worldId)) {
            reset();
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            reset();
            return;
        }
        if (pausedFor > 0L && !samples.isEmpty()) {
            Deque<Sample> shifted = new ArrayDeque<>(samples.size());
            for (Sample sample : samples) {
                shifted.addLast(sample.shiftedBy(pausedFor));
            }
            samples.clear();
            samples.addAll(shifted);
            if (lastSampleAt != Long.MIN_VALUE) {
                lastSampleAt += pausedFor;
            }
        }
        lastWorldId = worldId;
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLastPosition = true;
        lastAnalysisAt = Long.MIN_VALUE;
        cachedAnalysis = null;
    }

    private Window summarize(long fromInclusive, long toExclusive) {
        int[] movementDirectionCounts = new int[MOVEMENT_DIRECTION_BUCKETS];
        double[] movementDirectionDistances = new double[MOVEMENT_DIRECTION_BUCKETS];
        int sampleCount = 0;
        int movingSamples = 0;
        int movementDirectionSamples = 0;
        int largeMovementDirectionTurns = 0;
        double netMovementX = 0.0D;
        double netMovementZ = 0.0D;
        double totalMovementDistance = 0.0D;
        Float previousMovementDirection = null;
        List<Sample> windowSamples = new ArrayList<>();
        for (Sample sample : samples) {
            if (sample.at < fromInclusive || sample.at >= toExclusive) {
                continue;
            }
            sampleCount++;
            windowSamples.add(sample);
            if (sample.moving) {
                movingSamples++;
            }
            if (sample.movementDirectionBucket < 0) {
                continue;
            }
            movementDirectionSamples++;
            movementDirectionCounts[sample.movementDirectionBucket]++;
            movementDirectionDistances[sample.movementDirectionBucket] += sample.horizontalDistance;
            netMovementX += sample.deltaX;
            netMovementZ += sample.deltaZ;
            totalMovementDistance += sample.horizontalDistance;
            if (previousMovementDirection != null
                    && Math.abs(signedAngularDelta(previousMovementDirection, sample.movementDirection))
                    >= LARGE_MOVEMENT_DIRECTION_TURN_DEGREES) {
                largeMovementDirectionTurns++;
            }
            previousMovementDirection = sample.movementDirection;
        }
        return Window.from(sampleCount, movingSamples, movementDirectionCounts,
                movementDirectionDistances, movementDirectionSamples,
                largeMovementDirectionTurns, netMovementX, netMovementZ,
                totalMovementDistance, AfkMovementStatistics.from(windowSamples));
    }

    private static Pattern corroboratedPattern(Window previous, Window recent, Window shortWindow) {
        Pattern previousPattern = pattern(previous, WINDOW_MILLIS);
        Pattern recentPattern = pattern(recent, WINDOW_MILLIS);
        if (previousPattern == Pattern.ORDINARY_MOVEMENT || recentPattern == Pattern.ORDINARY_MOVEMENT
                || !hasLocalMovement(shortWindow.statistics, SHORT_WINDOW_MILLIS)) {
            return Pattern.ORDINARY_MOVEMENT;
        }
        if (previousPattern != recentPattern) {
            return Pattern.ORDINARY_MOVEMENT;
        }
        if (recentPattern == Pattern.VERTICAL_REPETITION
                && shortWindow.statistics.verticalReversals() < 4) {
            return Pattern.ORDINARY_MOVEMENT;
        }
        if (recentPattern != Pattern.VERTICAL_REPETITION
                && shortWindow.statistics.horizontalSamples() < 5) {
            return Pattern.ORDINARY_MOVEMENT;
        }
        if (recentPattern == Pattern.RANDOMIZED_LOCAL_MOVEMENT
                && previous.statistics.distributionDistance(recent.statistics) > 0.35D) {
            return Pattern.ORDINARY_MOVEMENT;
        }
        return recentPattern;
    }

    private static Pattern pattern(Window window, long duration) {
        AfkMovementStatistics statistics = window.statistics;
        double scale = (double) duration / WINDOW_MILLIS;
        if (!hasLocalMovement(statistics, duration)) {
            return Pattern.ORDINARY_MOVEMENT;
        }
        if (statistics.verticalSamples() >= statistics.movingSamples() * 0.75D
                && statistics.verticalReversals() >= 24 * scale) {
            return Pattern.VERTICAL_REPETITION;
        }
        if (statistics.horizontalSamples() < 24 * scale) {
            return Pattern.ORDINARY_MOVEMENT;
        }
        if (statistics.directionEntropy() >= 0.85D
                && statistics.conditionalDirectionEntropy() >= 0.80D
                && statistics.directionChanges() >= 24) {
            return Pattern.RANDOMIZED_LOCAL_MOVEMENT;
        }
        if ((statistics.periodicity() >= 0.80D
                || statistics.directionEntropy() >= 0.45D
                && statistics.conditionalDirectionEntropy() <= 0.25D)
                && statistics.directionChanges() >= 4 * scale) {
            return Pattern.LOCAL_REPETITION;
        }
        return window.isSuspicious() ? Pattern.UNIFORM_DIRECTION_CYCLING
                : Pattern.ORDINARY_MOVEMENT;
    }

    /** The latest minute may contain an incomplete loop; full windows require tighter closure. */
    private static boolean hasLocalMovement(AfkMovementStatistics statistics, long duration) {
        double scale = (double) duration / WINDOW_MILLIS;
        return statistics.movingSamples() >= 24 * scale
                && statistics.totalDistance() >= MIN_LOCAL_DISTANCE * scale
                && statistics.netMovementRatio() <= (duration < WINDOW_MILLIS ? 0.50D : MAX_NET_MOVEMENT_RATIO)
                && statistics.horizontalRange() <= MAX_LOCAL_RANGE
                && statistics.verticalRange() <= MAX_LOCAL_VERTICAL_RANGE;
    }

    private void removeExpired(long now) {
        long oldestAllowed = now - ANALYSIS_MILLIS;
        while (!samples.isEmpty() && samples.peekFirst().at < oldestAllowed) {
            samples.removeFirst();
        }
    }

    private static int movementDirectionBucket(float direction) {
        return bucket(direction + 45.0F, MOVEMENT_DIRECTION_BUCKETS);
    }

    private static int bucket(float direction, int bucketCount) {
        float normalized = normalizeYaw(direction);
        return Math.min(bucketCount - 1, (int) (normalized * bucketCount / 360.0F));
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    private static float signedAngularDelta(float from, float to) {
        float delta = (to - from) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        } else if (delta < -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }

    private static long elapsed(long now, long then) {
        return Math.max(0L, now - then);
    }

    record Sample(
            long at,
            boolean moving,
            int movementDirectionBucket,
            float movementDirection,
            double deltaX,
            double deltaZ,
            double horizontalDistance,
            double positionX, double positionY, double positionZ, double deltaY,
            boolean verticalMoving
    ) {

        private Sample shiftedBy(long delta) {
            return new Sample(at + delta, moving, movementDirectionBucket,
                    movementDirection, deltaX, deltaZ, horizontalDistance,
                    positionX, positionY, positionZ, deltaY, verticalMoving);
        }
    }

    private record Window(
            int samples,
            int movingSamples,
            int movementDirectionSamples,
            int occupiedMovementDirectionBuckets,
            double movementDirectionEntropy,
            double movementDirectionBucketCoefficientOfVariation,
            double movementDistanceEntropy,
            double movementDistanceBucketCoefficientOfVariation,
            double netMovementRatio,
            int largeMovementDirectionTurns,
            AfkMovementStatistics statistics
    ) {

        private static Window from(
                int samples,
                int movingSamples,
                int[] movementDirectionCounts,
                double[] movementDirectionDistances,
                int movementDirectionSamples,
                int largeMovementDirectionTurns,
                double netMovementX,
                double netMovementZ,
                double totalMovementDistance,
                AfkMovementStatistics statistics
        ) {
            if (samples == 0) {
                return new Window(0, 0, 0, 0, 0.0D, Double.POSITIVE_INFINITY,
                        0.0D, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0, statistics);
            }

            Distribution movementDirectionDistribution = Distribution.from(
                    movementDirectionCounts, movementDirectionSamples);
            Distribution movementDistanceDistribution = Distribution.from(
                    movementDirectionDistances, totalMovementDistance);
            double netMovementRatio = totalMovementDistance == 0.0D
                    ? Double.POSITIVE_INFINITY
                    : Math.hypot(netMovementX, netMovementZ) / totalMovementDistance;
            return new Window(
                    samples,
                    movingSamples,
                    movementDirectionSamples,
                    movementDirectionDistribution.occupiedBuckets,
                    movementDirectionDistribution.normalizedEntropy,
                    movementDirectionDistribution.coefficientOfVariation,
                    movementDistanceDistribution.normalizedEntropy,
                    movementDistanceDistribution.coefficientOfVariation,
                    netMovementRatio,
                    largeMovementDirectionTurns,
                    statistics
            );
        }

        private boolean isSuspicious() {
            return samples >= MIN_SAMPLES_PER_WINDOW
                    && (double) movingSamples / samples >= MIN_MOVING_SAMPLE_RATIO
                    && hasSuspiciousMovementDistribution();
        }

        private boolean hasSuspiciousMovementDistribution() {
            return movementDirectionSamples >= MIN_MOVEMENT_DIRECTION_SAMPLES
                    && occupiedMovementDirectionBuckets >= MIN_OCCUPIED_MOVEMENT_DIRECTION_BUCKETS
                    && movementDirectionEntropy >= MIN_NORMALIZED_MOVEMENT_DIRECTION_ENTROPY
                    && movementDirectionBucketCoefficientOfVariation
                    <= MAX_MOVEMENT_DIRECTION_BUCKET_COEFFICIENT_OF_VARIATION
                    && movementDistanceEntropy >= MIN_NORMALIZED_MOVEMENT_DISTANCE_ENTROPY
                    && movementDistanceBucketCoefficientOfVariation
                    <= MAX_MOVEMENT_DISTANCE_BUCKET_COEFFICIENT_OF_VARIATION
                    && netMovementRatio <= MAX_NET_MOVEMENT_RATIO
                    && largeMovementDirectionTurns >= MIN_LARGE_MOVEMENT_DIRECTION_TURNS;
        }
    }

    /** Descriptive labels deliberately avoid claiming that the player is a bot. */
    enum Pattern {
        INSUFFICIENT_DATA,
        ORDINARY_MOVEMENT,
        LOCAL_REPETITION,
        RANDOMIZED_LOCAL_MOVEMENT,
        VERTICAL_REPETITION,
        UNIFORM_DIRECTION_CYCLING
    }

    /**
     * Corroborated movement description with its three supporting windows.
     * A recommendation is a reversible reward-policy decision, never an AFK
     * candidate or proof of automation; accepted gameplay is evaluated separately.
     */
    record Analysis(
            Pattern pattern,
            AfkMovementStatistics previous,
            AfkMovementStatistics recent,
            AfkMovementStatistics latest
    ) {
        boolean rewardRestrictionRecommended() {
            return pattern != Pattern.INSUFFICIENT_DATA && pattern != Pattern.ORDINARY_MOVEMENT;
        }
    }

    private record Distribution(
            int occupiedBuckets,
            double normalizedEntropy,
            double coefficientOfVariation
    ) {

        private static Distribution from(int[] counts, int samples) {
            double[] values = new double[counts.length];
            for (int index = 0; index < counts.length; index++) {
                values[index] = counts[index];
            }
            return from(values, samples);
        }

        private static Distribution from(double[] values, double total) {
            if (total <= 0.0D) {
                return new Distribution(0, 0.0D, Double.POSITIVE_INFINITY);
            }

            int occupied = 0;
            double entropy = 0.0D;
            double mean = total / values.length;
            double squaredDeviation = 0.0D;
            for (double value : values) {
                double deviation = value - mean;
                squaredDeviation += deviation * deviation;
                if (value <= 0.0D) {
                    continue;
                }
                occupied++;
                double probability = value / total;
                entropy -= probability * Math.log(probability);
            }
            double normalizedEntropy = entropy / Math.log(values.length);
            double standardDeviation = Math.sqrt(squaredDeviation / values.length);
            return new Distribution(occupied, normalizedEntropy, standardDeviation / mean);
        }
    }
}
