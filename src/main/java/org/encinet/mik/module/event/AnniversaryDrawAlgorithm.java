package org.encinet.mik.module.event;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/** Pure odds, stock-release and prize-selection rules for the anniversary draw. */
final class AnniversaryDrawAlgorithm {
    static final long RELEASE_SLOT_MILLIS = Duration.ofHours(2).toMillis();
    static final double MAX_BASE_PROBABILITY = 0.58D;

    private static final double RELEASE_CURVE_EXPONENT = 0.72D;
    private static final double INITIAL_RELEASE_FRACTION = 0.08D;
    private static final double MIN_BASE_PROBABILITY = 0.02D;
    private static final double PERSONAL_ODDS_COEFFICIENT = 0.13D;
    private static final double PERSONAL_HISTORY_STRENGTH = 0.75D;
    private static final double MIN_PERSONAL_WEIGHT = 0.78D;
    private static final double MAX_PERSONAL_WEIGHT = 1.32D;

    private AnniversaryDrawAlgorithm() { }

    static double calculateBaseProbability(ControllerSnapshot snapshot) {
        if (snapshot.remainingStock <= 0 || snapshot.availableStock <= 0) {
            return 0.0D;
        }
        double expectedWindowOpportunities = Math.max(1.0D,
                snapshot.opportunitiesPerHour * RELEASE_SLOT_MILLIS / 3_600_000.0D);
        double desiredWindowWins = snapshot.releaseInWindow + 0.35D * snapshot.availableStock;
        double speedProbability = desiredWindowWins / expectedWindowOpportunities;

        double predictedRemainingOpportunities = Math.max(
                snapshot.remainingStock,
                Math.max(snapshot.knownFutureOpportunities,
                        snapshot.opportunitiesPerHour * snapshot.remainingHours));
        double stockProbability = snapshot.remainingStock / predictedRemainingOpportunities;
        return Math.clamp(0.80D * speedProbability + 0.20D * stockProbability,
                MIN_BASE_PROBABILITY, MAX_BASE_PROBABILITY);
    }

    static double calibratedPersonalOffset(double baseProbability, List<Double> weights) {
        if (weights.isEmpty() || baseProbability <= 0.0D || baseProbability >= 1.0D) {
            return 0.0D;
        }
        double baseLogit = logit(baseProbability);
        double low = -2.0D;
        double high = 2.0D;
        for (int iteration = 0; iteration < 24; iteration++) {
            double middle = (low + high) / 2.0D;
            double average = weights.stream()
                    .mapToDouble(weight -> sigmoid(baseLogit
                            + PERSONAL_HISTORY_STRENGTH * Math.log(weight)
                            + middle))
                    .average()
                    .orElse(baseProbability);
            if (average > baseProbability) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return (low + high) / 2.0D;
    }

    static double personalWeight(int wins, int losses) {
        double score = Math.clamp(losses - 1.25D * wins, -2.0D, 2.0D);
        return Math.clamp(Math.exp(PERSONAL_ODDS_COEFFICIENT * score),
                MIN_PERSONAL_WEIGHT, MAX_PERSONAL_WEIGHT);
    }

    static double personalizedProbability(double baseProbability, double weight,
                                          double calibrationOffset) {
        if (baseProbability <= 0.0D || baseProbability >= 1.0D) {
            return baseProbability;
        }
        double weightedLogOdds = logit(baseProbability)
                + PERSONAL_HISTORY_STRENGTH * Math.log(weight)
                + calibrationOffset;
        return sigmoid(weightedLogOdds);
    }

    static int releasedPrizeIndex(int[] available, int selected) {
        if (Arrays.stream(available).anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("Released prize availability cannot be negative");
        }
        int total = Arrays.stream(available).sum();
        if (selected < 0 || selected >= total) {
            throw new IllegalArgumentException("Selected prize offset is out of bounds");
        }
        for (int index = 0; index < available.length; index++) {
            if (selected < available[index]) {
                return index;
            }
            selected -= available[index];
        }
        throw new IllegalStateException("Released prize selection was out of bounds");
    }

    static int availableReleasedStock(int totalStock, int remainingStock,
                                      int releasedStock, int borrowLimit) {
        int awarded = totalStock - remainingStock;
        int releaseLimit = Math.min(totalStock, releasedStock + borrowLimit);
        return Math.clamp(releaseLimit - awarded, 0, remainingStock);
    }

    static int[] generateReleaseSlots(int stock, long seed, int slotCount) {
        SplittableRandom generator = new SplittableRandom(seed);
        int[] slots = new int[stock];
        for (int index = 0; index < stock; index++) {
            double quantile = (index + 0.15D + 0.70D * generator.nextDouble()) / stock;
            double progress = quantile <= INITIAL_RELEASE_FRACTION
                    ? 0.0D
                    : Math.pow((quantile - INITIAL_RELEASE_FRACTION)
                            / (1.0D - INITIAL_RELEASE_FRACTION), 1.0D / RELEASE_CURVE_EXPONENT);
            double jitter = generator.nextDouble(-0.75D, 0.75D);
            slots[index] = (int) Math.clamp(Math.floor(progress * slotCount + jitter),
                    0.0D, slotCount - 1.0D);
        }
        Arrays.sort(slots);
        return slots;
    }

    static double limitOddsChange(double previous, double candidate,
                                  double minimumFactor, double maximumFactor) {
        double previousOdds = previous / (1.0D - previous);
        double candidateOdds = candidate / (1.0D - candidate);
        double limitedOdds = Math.clamp(candidateOdds,
                previousOdds * minimumFactor, previousOdds * maximumFactor);
        return limitedOdds / (1.0D + limitedOdds);
    }

    private static double logit(double probability) {
        return Math.log(probability / (1.0D - probability));
    }

    private static double sigmoid(double value) {
        return 1.0D / (1.0D + Math.exp(-value));
    }

    static final class ControllerSnapshot {
        final int remainingStock;
        final int availableStock;
        final int releaseInWindow;
        final double opportunitiesPerHour;
        final double remainingHours;
        final int knownFutureOpportunities;

        ControllerSnapshot(int remainingStock, int availableStock, int releaseInWindow,
                           double opportunitiesPerHour, double remainingHours,
                           int knownFutureOpportunities) {
            this.remainingStock = remainingStock;
            this.availableStock = availableStock;
            this.releaseInWindow = releaseInWindow;
            this.opportunitiesPerHour = opportunitiesPerHour;
            this.remainingHours = remainingHours;
            this.knownFutureOpportunities = knownFutureOpportunities;
        }
    }
}
