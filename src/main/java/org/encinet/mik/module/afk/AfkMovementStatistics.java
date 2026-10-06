package org.encinet.mik.module.afk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Descriptive statistics for a time-bounded movement window, never a bot probability.
 *
 * <p>Marginal direction entropy describes balance, not randomness: an exact square
 * cycle and independent uniform directions can both have maximal marginal entropy.
 * Conditional entropy is therefore computed over consecutive <em>direction changes</em>,
 * excluding repeated samples of a held direction. It is normalized by log(K - 1)
 * because a changed direction cannot equal its predecessor. This reduces the
 * dependence on walking speed/held-key duration but is still only a heuristic.
 *
 * <p>Lag agreement is chance-corrected categorical agreement, not numeric correlation
 * of angle buckets; treating bucket indices as real angles would mishandle wrap-around.
 * Spatial extent, vertical reversals and sample coverage provide independent context.
 * A missing/nonmoving sample breaks direction transitions. Insufficient data is kept
 * distinguishable from ordinary movement and must never be interpreted as inactivity.
 * Conditional entropy is {@code NaN} below the minimum transition count: absence
 * of transitions is not evidence of perfect predictability.
 */
record AfkMovementStatistics(
        int samples,
        long coverageMillis,
        int movingSamples,
        int horizontalSamples,
        int verticalSamples,
        double totalDistance,
        double netMovementRatio,
        double horizontalRange,
        double verticalRange,
        int occupiedCells,
        double directionEntropy,
        double conditionalDirectionEntropy,
        double periodicity,
        int directionChanges,
        int verticalReversals,
        List<Double> directionDistribution
) {

    private static final double CELL_SIZE = 4.0D;
    private static final int[] PERIOD_LAGS = {2, 4, 8, 16, 20, 32, 40, 60, 80, 160};
    private static final int MIN_LAG_PAIRS = 60;
    private static final int MIN_DIRECTION_CHANGES = 24;

    AfkMovementStatistics {
        directionDistribution = List.copyOf(directionDistribution);
    }

    static AfkMovementStatistics from(List<AfkBehaviorAnalyzer.Sample> observations) {
        int directions = AfkBehaviorAnalyzer.MOVEMENT_DIRECTION_BUCKETS;
        int[] counts = new int[directions];
        int[][] transitions = new int[directions][directions];
        List<Integer> sequence = new ArrayList<>(observations.size());
        Set<Cell> cells = new HashSet<>();
        int moving = 0;
        int horizontal = 0;
        int vertical = 0;
        int changes = 0;
        int verticalReversals = 0;
        int previousDirection = -1;
        int previousVerticalSign = 0;
        double distance = 0.0D;
        double netX = 0.0D;
        double netY = 0.0D;
        double netZ = 0.0D;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (AfkBehaviorAnalyzer.Sample sample : observations) {
            int direction = sample.movementDirectionBucket();
            sequence.add(direction);
            if (sample.moving() || sample.verticalMoving()) {
                moving++;
                distance += Math.sqrt(sample.deltaX() * sample.deltaX()
                        + sample.deltaY() * sample.deltaY() + sample.deltaZ() * sample.deltaZ());
                netX += sample.deltaX();
                netY += sample.deltaY();
                netZ += sample.deltaZ();
                minX = Math.min(minX, sample.positionX());
                minY = Math.min(minY, sample.positionY());
                minZ = Math.min(minZ, sample.positionZ());
                maxX = Math.max(maxX, sample.positionX());
                maxY = Math.max(maxY, sample.positionY());
                maxZ = Math.max(maxZ, sample.positionZ());
                cells.add(new Cell(cell(sample.positionX()), cell(sample.positionY()), cell(sample.positionZ())));
            }
            if (direction >= 0) {
                horizontal++;
                counts[direction]++;
                if (previousDirection >= 0 && direction != previousDirection) {
                    transitions[previousDirection][direction]++;
                    changes++;
                }
            }
            previousDirection = direction;
            if (sample.verticalMoving()) {
                vertical++;
                int sign = sample.deltaY() > 0.0D ? 1 : -1;
                if (previousVerticalSign != 0 && previousVerticalSign != sign) {
                    verticalReversals++;
                }
                previousVerticalSign = sign;
            } else {
                previousVerticalSign = 0;
            }
        }

        List<Double> distribution = new ArrayList<>(directions);
        for (int count : counts) {
            distribution.add(horizontal == 0 ? 0.0D : (double) count / horizontal);
        }
        long coverage = observations.size() < 2 ? 0L
                : observations.getLast().at() - observations.getFirst().at();
        return new AfkMovementStatistics(
                observations.size(), coverage, moving, horizontal, vertical, distance,
                distance == 0.0D ? 1.0D
                        : Math.sqrt(netX * netX + netY * netY + netZ * netZ) / distance,
                moving == 0 ? 0.0D : Math.hypot(maxX - minX, maxZ - minZ),
                moving == 0 ? 0.0D : maxY - minY,
                cells.size(), entropy(counts, horizontal) / Math.log(directions),
                conditionalEntropy(transitions, changes, directions),
                periodicity(sequence, distribution), changes, verticalReversals, distribution);
    }

    /** Requires both sample density and temporal coverage; a late burst is not a full window. */
    boolean hasSufficientCoverage(long windowMillis) {
        return samples >= windowMillis / AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS * 0.60D
                && coverageMillis >= windowMillis * 0.80D;
    }

    /** Tests proximity of distributions, not the identity of the generating player. */
    double distributionDistance(AfkMovementStatistics other) {
        double difference = 0.0D;
        for (int index = 0; index < directionDistribution.size(); index++) {
            difference += Math.abs(directionDistribution.get(index)
                    - other.directionDistribution.get(index));
        }
        return difference;
    }

    private static long cell(double coordinate) {
        return (long) Math.floor(coordinate / CELL_SIZE);
    }

    private static double entropy(int[] counts, int total) {
        if (total == 0) {
            return 0.0D;
        }
        double entropy = 0.0D;
        for (int count : counts) {
            if (count > 0) {
                double probability = (double) count / total;
                entropy -= probability * Math.log(probability);
            }
        }
        return entropy;
    }

    private static double conditionalEntropy(int[][] transitions, int changes, int directions) {
        if (changes < MIN_DIRECTION_CHANGES) {
            return Double.NaN;
        }
        double result = 0.0D;
        for (int[] row : transitions) {
            int rowTotal = 0;
            for (int count : row) {
                rowTotal += count;
            }
            result += (double) rowTotal / changes * entropy(row, rowTotal);
        }
        return result / Math.log(directions - 1);
    }

    private static double periodicity(List<Integer> sequence, List<Double> distribution) {
        double chanceAgreement = 0.0D;
        for (double probability : distribution) {
            chanceAgreement += probability * probability;
        }
        if (chanceAgreement >= 1.0D - 1.0E-9D) {
            return 0.0D;
        }
        double maximum = 0.0D;
        for (int lag : PERIOD_LAGS) {
            int pairs = 0;
            int matching = 0;
            for (int index = lag; index < sequence.size(); index++) {
                int previous = sequence.get(index - lag);
                int current = sequence.get(index);
                if (previous >= 0 && current >= 0) {
                    pairs++;
                    if (previous == current) {
                        matching++;
                    }
                }
            }
            if (pairs >= MIN_LAG_PAIRS) {
                double agreement = (double) matching / pairs;
                maximum = Math.max(maximum,
                        (agreement - chanceAgreement) / (1.0D - chanceAgreement));
            }
        }
        return maximum;
    }

    private record Cell(long cellX, long cellY, long cellZ) {
    }
}
