package org.encinet.mik.module.afk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;

final class AfkBehaviorAnalyzer {

    static final long SAMPLE_INTERVAL_MILLIS = 500L;
    static final long WINDOW_MILLIS = 5L * 60L * 1_000L;
    static final long ANALYSIS_MILLIS = 2L * WINDOW_MILLIS;
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

    void record(
            UUID worldId,
            double x,
            double y,
            double z,
            float ignoredYaw,
            boolean movementInputActive,
            long now
    ) {
        if (lastSampleAt != Long.MIN_VALUE
                && elapsed(now, lastSampleAt) < SAMPLE_INTERVAL_MILLIS) {
            return;
        }

        boolean sameWorld = hasLastPosition && Objects.equals(lastWorldId, worldId);
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
                horizontalDistance
        ));
        lastSampleAt = now;
        lastWorldId = worldId;
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLastPosition = true;
        removeExpired(now);
    }

    boolean isLikelyAutomated(long now, long lastIntentionalActivityAt) {
        if (elapsed(now, lastIntentionalActivityAt) < ANALYSIS_MILLIS) {
            return false;
        }

        removeExpired(now);
        long recentWindowStart = now - WINDOW_MILLIS;
        long previousWindowStart = now - ANALYSIS_MILLIS;
        Window previous = summarize(previousWindowStart, recentWindowStart);
        Window recent = summarize(recentWindowStart, now + 1L);
        return previous.isSuspicious() && recent.isSuspicious();
    }

    void reset() {
        samples.clear();
        lastSampleAt = Long.MIN_VALUE;
        lastWorldId = null;
        hasLastPosition = false;
    }

    void resumeAfter(long pausedFor, UUID worldId, double x, double y, double z) {
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
        for (Sample sample : samples) {
            if (sample.at < fromInclusive || sample.at >= toExclusive) {
                continue;
            }
            sampleCount++;
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
                totalMovementDistance);
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

    private record Sample(
            long at,
            boolean moving,
            int movementDirectionBucket,
            float movementDirection,
            double deltaX,
            double deltaZ,
            double horizontalDistance
    ) {

        private Sample shiftedBy(long delta) {
            return new Sample(at + delta, moving, movementDirectionBucket,
                    movementDirection, deltaX, deltaZ, horizontalDistance);
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
            int largeMovementDirectionTurns
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
                double totalMovementDistance
        ) {
            if (samples == 0) {
                return new Window(0, 0, 0, 0, 0.0D, Double.POSITIVE_INFINITY,
                        0.0D, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0);
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
                    largeMovementDirectionTurns
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
