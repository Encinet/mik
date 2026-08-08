package org.encinet.mik.module.music.rhythm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a robust latency estimate from a rolling window of rhythm phrases.
 *
 * <p>Individual taps inside one learned phrase are correlated, so confidence is
 * still accumulated per cycle. A half-covered current cycle can participate as
 * soon as it is internally stable, however, and only the latest stable run is
 * retained. This lets a consistent player finish without waiting for several
 * complete 6.4-second phrases while stale practice taps cannot pin the estimate
 * forever.</p>
 */
final class RhythmLatencyCalibration {
    static final int REQUIRED_SAMPLES = 10;
    static final int MINIMUM_OFFSET_MILLIS = -250;
    static final int MAXIMUM_OFFSET_MILLIS = 350;
    static final int MAXIMUM_RELIABLE_DEVIATION_MILLIS = 65;
    static final int MAXIMUM_CONFIDENCE_RADIUS_MILLIS = 25;
    static final int MAXIMUM_STABLE_DRIFT_MILLIS = 35;
    static final double MINIMUM_EFFECTIVE_CYCLES = 1.0;
    static final int MINIMUM_RECENT_STABLE_CYCLES = 2;
    static final int PRECISE_UNCERTAINTY_FLOOR_MILLIS = 10;
    static final int COARSE_UNCERTAINTY_FLOOR_MILLIS = 25;

    private static final int MAXIMUM_OBSERVATION_ERROR_MILLIS = 450;
    private static final int MINIMUM_OUTLIER_WINDOW_MILLIS = 35;
    private static final int MAXIMUM_OUTLIER_WINDOW_MILLIS = 120;
    private static final int MAXIMUM_EVIDENCE_CYCLES = 6;
    private static final double MINIMUM_CYCLE_COVERAGE = 0.50;
    private static final double MAD_STDDEV_COEFFICIENT = 1.4826;
    private static final double MEDIAN_STANDARD_ERROR_FACTOR = 1.253;
    private static final double CONFIDENCE_Z_95 = 1.96;
    private static final int MINIMUM_EFFECTIVE_SAMPLE_COUNT = 4;

    private final Set<Long> sampledCueIds = new HashSet<>();
    private final List<Observation> observations = new ArrayList<>();
    private long closedThroughCycle = -1L;
    private long newestObservedCycle = -1L;
    private int acceptedObservationCount;
    private int rejectedRangeCount;
    private Analysis cachedAnalysis;

    SampleResult record(Observation observation) {
        java.util.Objects.requireNonNull(observation, "observation");
        if (complete()) return SampleResult.COMPLETE;
        if (!sampledCueIds.add(observation.cueId())) {
            return SampleResult.DUPLICATE;
        }
        if (Math.abs(observation.errorMillis())
                > MAXIMUM_OBSERVATION_ERROR_MILLIS) {
            rejectedRangeCount++;
            return SampleResult.OUT_OF_RANGE;
        }
        observations.add(observation);
        acceptedObservationCount++;
        newestObservedCycle = Math.max(newestObservedCycle,
                observation.cycleIndex());
        long oldestRetainedCycle = Math.max(0L,
                newestObservedCycle - MAXIMUM_EVIDENCE_CYCLES + 1L);
        observations.removeIf(value ->
                value.cycleIndex() < oldestRetainedCycle);
        cachedAnalysis = null;
        if (complete()) return SampleResult.COMPLETE;
        return adapting() ? SampleResult.ADAPTING : SampleResult.ACCEPTED;
    }

    /** Marks every earlier phrase as closed while the current partial phrase grows. */
    void advanceToCycle(long currentCycle) {
        // Reliability is terminal for one modality. The service switches stages
        // immediately, but latching here also prevents a following scheduler tick
        // from invalidating the estimate before that transition is observed.
        if (complete()) return;
        long newlyClosed = Math.max(-1L, currentCycle - 1L);
        if (newlyClosed <= closedThroughCycle) return;
        closedThroughCycle = newlyClosed;
        cachedAnalysis = null;
    }

    int sampleCount() {
        return analysis().estimate().inlierCount();
    }

    int observationCount() {
        return analysis().estimate().observationCount();
    }

    boolean adapting() {
        return analysis().latestStableRun() < MINIMUM_RECENT_STABLE_CYCLES;
    }

    boolean outOfSupportedRange() {
        Optional<Estimate> estimate = currentEstimate();
        return estimate.isPresent() && !estimate.get().withinSupportedRange();
    }

    int rejectedCount() {
        return rejectedRangeCount + Math.max(0,
                observations.size() - analysis().estimate().inlierCount());
    }

    boolean complete() {
        return analysis().estimate().reliable();
    }

    boolean sampled(long cueId) {
        return sampledCueIds.contains(cueId);
    }

    Optional<Estimate> currentEstimate() {
        Estimate estimate = analysis().estimate();
        return estimate.observationCount() == 0
                ? Optional.empty() : Optional.of(estimate);
    }

    Estimate estimate() {
        Estimate estimate = currentEstimate().orElseThrow(() ->
                new IllegalStateException("latency calibration has no samples"));
        if (!estimate.reliable()) {
            throw new IllegalStateException("latency calibration is not confident yet");
        }
        return estimate;
    }

    /** Starts the independent measurement for the next modality. */
    void reset() {
        sampledCueIds.clear();
        observations.clear();
        closedThroughCycle = -1L;
        newestObservedCycle = -1L;
        acceptedObservationCount = 0;
        rejectedRangeCount = 0;
        cachedAnalysis = null;
    }

    private Analysis analysis() {
        if (cachedAnalysis != null) return cachedAnalysis;
        List<CycleSample> cycles = completedCycles();
        if (cycles.isEmpty()) {
            Estimate empty = new Estimate(0, 0, 0, acceptedObservationCount,
                    Integer.MAX_VALUE, Integer.MAX_VALUE, 0.0, 0, true);
            cachedAnalysis = new Analysis(empty, 0);
            return cachedAnalysis;
        }

        List<WeightedCycle> weighted = applyEntrainmentWeights(cycles,
                closedThroughCycle);
        List<WeightedCycle> eligible = weighted.stream()
                .filter(cycle -> cycle.weight() > 0.0).toList();
        int latestStableRun = 0;
        for (int index = weighted.size() - 1; index >= 0; index--) {
            if (weighted.get(index).weight() <= 0.0) continue;
            latestStableRun = weighted.get(index).stableRun();
            break;
        }
        if (eligible.isEmpty()) {
            Estimate empty = new Estimate(0, 0, 0, acceptedObservationCount,
                    Integer.MAX_VALUE, Integer.MAX_VALUE, 0.0,
                    latestStableRun, true);
            cachedAnalysis = new Analysis(empty, latestStableRun);
            return cachedAnalysis;
        }

        int preliminaryCenter = weightedMedian(eligible);
        int cycleWindowMillis = cycleOutlierWindowMillis(eligible, preliminaryCenter);
        List<WeightedCycle> inlierCycles = eligible.stream()
                .filter(cycle -> Math.abs(cycle.sample().centerMillis()
                        - preliminaryCenter) <= cycleWindowMillis)
                .toList();
        if (inlierCycles.isEmpty()) inlierCycles = eligible;

        Set<Long> inlierCycleIds = inlierCycles.stream()
                .map(cycle -> cycle.sample().cycleIndex())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Observation> cycleObservations = observations.stream()
                .filter(value -> inlierCycleIds.contains(value.cycleIndex()))
                .toList();
        int initialCenter = cycleObservations.isEmpty() ? preliminaryCenter
                : median(cycleObservations.stream()
                .map(Observation::errorMillis).toList());
        int observationWindowMillis = observationOutlierWindowMillis(
                cycleObservations, initialCenter);
        List<Observation> inlierObservations = cycleObservations.stream()
                .filter(value -> Math.abs(value.errorMillis() - initialCenter)
                        <= observationWindowMillis)
                .toList();
        int center = initialCenter;
        if (!inlierObservations.isEmpty()) {
            center = roundedMean(inlierObservations.stream()
                    .map(Observation::errorMillis).toList());
            int refinedCenter = center;
            int refinedWindow = observationOutlierWindowMillis(
                    cycleObservations, refinedCenter);
            inlierObservations = cycleObservations.stream()
                    .filter(value -> Math.abs(value.errorMillis() - refinedCenter)
                            <= refinedWindow)
                    .toList();
            if (!inlierObservations.isEmpty()) {
                center = roundedMean(inlierObservations.stream()
                        .map(Observation::errorMillis).toList());
            }
        }
        int estimatedCenter = center;
        int deviation = inlierObservations.isEmpty() ? Integer.MAX_VALUE
                : median(inlierObservations.stream()
                .map(value -> Math.abs(value.errorMillis()
                        - estimatedCenter)).toList());
        double effectiveCycles = inlierCycles.stream()
                .mapToDouble(WeightedCycle::weight).sum();
        int confidence = confidenceRadius(inlierCycles, inlierObservations,
                estimatedCenter,
                inlierObservations.stream().anyMatch(Observation::coarseTiming));
        int drift = cycleDrift(inlierCycles);
        boolean withinRange = estimatedCenter >= MINIMUM_OFFSET_MILLIS
                && estimatedCenter <= MAXIMUM_OFFSET_MILLIS;
        int recentStable = recentStableCycles(inlierCycles);
        Estimate estimate = new Estimate(estimatedCenter, deviation,
                inlierObservations.size(), acceptedObservationCount,
                confidence, drift,
                effectiveCycles, recentStable, withinRange);
        cachedAnalysis = new Analysis(estimate, latestStableRun);
        return cachedAnalysis;
    }

    private static int cycleOutlierWindowMillis(List<WeightedCycle> cycles,
                                               int centerMillis) {
        double mad = inlierMedianAbsoluteDeviation(cycles, centerMillis);
        if (!Double.isFinite(mad) || mad <= 0.0) {
            return MINIMUM_OUTLIER_WINDOW_MILLIS;
        }
        return Math.clamp((int) Math.ceil(mad * 2.5),
                MINIMUM_OUTLIER_WINDOW_MILLIS,
                MAXIMUM_OUTLIER_WINDOW_MILLIS);
    }

    private static int observationOutlierWindowMillis(
            List<Observation> observations, int centerMillis) {
        List<Integer> deviations = observations.stream()
                .map(value -> Math.abs(value.errorMillis() - centerMillis)).toList();
        if (deviations.isEmpty()) {
            return MINIMUM_OUTLIER_WINDOW_MILLIS;
        }
        int mad = median(deviations);
        return Math.clamp((int) Math.ceil(mad * 2.5),
                MINIMUM_OUTLIER_WINDOW_MILLIS,
                MAXIMUM_OUTLIER_WINDOW_MILLIS);
    }

    private List<CycleSample> completedCycles() {
        Map<Long, List<Observation>> grouped = new LinkedHashMap<>();
        observations.stream()
                .sorted(Comparator.comparingLong(Observation::cycleIndex)
                        .thenComparingInt(Observation::cueIndex))
                .forEach(value -> grouped.computeIfAbsent(value.cycleIndex(),
                        ignored -> new ArrayList<>()).add(value));
        List<CycleSample> result = new ArrayList<>(grouped.size());
        grouped.forEach((cycleIndex, samples) -> {
            int cueCount = samples.stream().mapToInt(Observation::cuesPerCycle)
                    .max().orElse(1);
            double coverage = Math.min(1.0, samples.size() / (double) cueCount);
            int center = median(samples.stream().map(Observation::errorMillis).toList());
            int deviation = median(samples.stream()
                    .map(value -> Math.abs(value.errorMillis() - center)).toList());
            boolean eligible = coverage >= MINIMUM_CYCLE_COVERAGE
                    && deviation <= MAXIMUM_RELIABLE_DEVIATION_MILLIS;
            result.add(new CycleSample(cycleIndex, center, deviation,
                    coverage, samples.size(), eligible));
        });
        return List.copyOf(result);
    }

    private static List<WeightedCycle> applyEntrainmentWeights(
            List<CycleSample> cycles, long closedThroughCycle) {
        List<WeightedCycle> result = new ArrayList<>(cycles.size());
        Integer previousCenter = null;
        Long previousCycle = null;
        int stableRun = 0;
        for (CycleSample cycle : cycles) {
            if (!cycle.eligible()) {
                stableRun = 0;
                previousCenter = null;
                result.add(new WeightedCycle(cycle, 0.0, 0));
                continue;
            }
            stableRun = previousCenter != null && previousCycle != null
                    && cycle.cycleIndex() == previousCycle + 1L
                    && Math.abs(cycle.centerMillis() - previousCenter)
                    <= MAXIMUM_STABLE_DRIFT_MILLIS
                    ? stableRun + 1 : 1;
            previousCenter = cycle.centerMillis();
            previousCycle = cycle.cycleIndex();
            double entrainment = Math.min(1.0, stableRun / 2.0);
            result.add(new WeightedCycle(cycle,
                    entrainment * cycle.coverage(), stableRun));
        }

        int latest = -1;
        for (int index = result.size() - 1; index >= 0; index--) {
            if (result.get(index).weight() > 0.0) {
                latest = index;
                break;
            }
        }
        if (latest < 0
                || result.get(latest).sample().cycleIndex()
                < closedThroughCycle) {
            return result.stream()
                    .map(value -> new WeightedCycle(value.sample(), 0.0, 0))
                    .toList();
        }

        int stableStart = latest - result.get(latest).stableRun() + 1;
        List<WeightedCycle> latestRun = new ArrayList<>(result.size());
        for (int index = 0; index < result.size(); index++) {
            WeightedCycle value = result.get(index);
            latestRun.add(index >= stableStart && index <= latest
                    ? value : new WeightedCycle(value.sample(), 0.0, 0));
        }
        return List.copyOf(latestRun);
    }

    private static int weightedMedian(List<WeightedCycle> cycles) {
        List<WeightedCycle> ordered = cycles.stream()
                .sorted(Comparator.comparingInt(value ->
                        value.sample().centerMillis())).toList();
        double total = ordered.stream().mapToDouble(WeightedCycle::weight).sum();
        double target = total * 0.5;
        double cumulative = 0.0;
        for (WeightedCycle cycle : ordered) {
            cumulative += cycle.weight();
            if (cumulative >= target) return cycle.sample().centerMillis();
        }
        return ordered.getLast().sample().centerMillis();
    }

    private static int confidenceRadius(List<WeightedCycle> cycles,
                                       List<Observation> inlierObservations,
                                       int center,
                                       boolean coarseTiming) {
        int floor = coarseTiming ? COARSE_UNCERTAINTY_FLOOR_MILLIS
                : PRECISE_UNCERTAINTY_FLOOR_MILLIS;
        if (cycles.isEmpty() || inlierObservations.size()
                < MINIMUM_EFFECTIVE_SAMPLE_COUNT) {
            return Integer.MAX_VALUE;
        }
        double totalWeight = cycles.stream().mapToDouble(WeightedCycle::weight).sum();
        if (totalWeight <= 0.0) return Integer.MAX_VALUE;
        double mad = weightedMedianAbsoluteDeviation(inlierObservations, cycles, center);
        if (!Double.isFinite(mad) || mad <= 0.0) {
            return floor;
        }
        double effectiveSamples = effectiveObservationSamples(cycles,
                inlierObservations);
        if (effectiveSamples < MINIMUM_EFFECTIVE_SAMPLE_COUNT) {
            return Integer.MAX_VALUE;
        }
        double standardError = (MAD_STDDEV_COEFFICIENT * mad
                * MEDIAN_STANDARD_ERROR_FACTOR) / Math.sqrt(effectiveSamples);
        int radius = (int) Math.ceil(Math.max(0.0,
                CONFIDENCE_Z_95 * standardError));
        return Math.max(floor, radius);
    }

    private static double effectiveObservationSamples(
            List<WeightedCycle> cycles,
            List<Observation> observations) {
        Map<Long, Double> weightByCycle = new LinkedHashMap<>();
        for (WeightedCycle cycle : cycles) {
            weightByCycle.put(cycle.sample().cycleIndex(), cycle.weight());
        }
        double weightedSamples = observations.stream()
                .mapToDouble(value -> weightByCycle.getOrDefault(
                        value.cycleIndex(), 0.0)).sum();
        return Math.max(1.0, weightedSamples);
    }

    private static double weightedMedianAbsoluteDeviation(
            List<Observation> observations,
            List<WeightedCycle> cycles,
            int centerMillis) {
        if (observations.isEmpty()) {
            return Double.NaN;
        }

        Map<Long, Double> weightByCycle = new LinkedHashMap<>();
        for (WeightedCycle cycle : cycles) {
            weightByCycle.put(cycle.sample().cycleIndex(), cycle.weight());
        }

        List<WeightedDeviation> sorted = observations.stream()
                .map(value -> new WeightedDeviation(
                        Math.abs(value.errorMillis() - centerMillis),
                        weightByCycle.getOrDefault(value.cycleIndex(), 0.0)))
                .filter(sample -> sample.weight() > 0.0)
                .sorted(Comparator.comparingInt(WeightedDeviation::deviation))
                .toList();
        if (sorted.isEmpty()) {
            return Double.NaN;
        }

        double totalWeight = sorted.stream()
                .mapToDouble(WeightedDeviation::weight).sum();
        if (totalWeight <= 0.0) return Double.NaN;
        double target = totalWeight * 0.5;
        double cumulative = 0.0;
        for (WeightedDeviation sample : sorted) {
            cumulative += sample.weight();
            if (cumulative >= target) return sample.deviation();
        }
        return sorted.getLast().deviation();
    }

    private static double inlierMedianAbsoluteDeviation(List<WeightedCycle> cycles,
                                                       int centerMillis) {
        if (cycles.isEmpty()) return Double.NaN;

        List<WeightedDeviation> sorted = cycles.stream()
                .filter(cycle -> cycle.weight() > 0.0)
                .map(cycle -> new WeightedDeviation(
                        Math.abs(cycle.sample().centerMillis() - centerMillis),
                        cycle.weight()))
                .sorted(Comparator.comparingInt(WeightedDeviation::deviation))
                .toList();
        if (sorted.isEmpty()) return Double.NaN;

        double totalWeight = sorted.stream()
                .mapToDouble(WeightedDeviation::weight).sum();
        if (totalWeight <= 0.0) return Double.NaN;
        double target = totalWeight * 0.5;
        double cumulative = 0.0;
        for (WeightedDeviation sample : sorted) {
            cumulative += sample.weight();
            if (cumulative >= target) return sample.deviation();
        }
        return sorted.getLast().deviation();
    }

    private static int cycleDrift(List<WeightedCycle> cycles) {
        if (cycles.size() < 2) return Integer.MAX_VALUE;
        int middle = cycles.size() / 2;
        int early = weightedMedian(cycles.subList(0, middle));
        int recent = weightedMedian(cycles.subList(middle, cycles.size()));
        return Math.abs(recent - early);
    }

    private static int recentStableCycles(List<WeightedCycle> cycles) {
        if (cycles.isEmpty()) return 0;
        int count = 0;
        Integer previous = null;
        for (int index = cycles.size() - 1; index >= 0; index--) {
            WeightedCycle cycle = cycles.get(index);
            int center = cycle.sample().centerMillis();
            if (cycle.weight() <= 0.0
                    || previous != null && Math.abs(center - previous)
                    > MAXIMUM_STABLE_DRIFT_MILLIS) break;
            count++;
            previous = center;
        }
        return count;
    }

    private static int median(List<Integer> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        int[] ordered = values.stream().mapToInt(Integer::intValue).sorted().toArray();
        int middle = ordered.length / 2;
        return ordered.length % 2 == 0
                ? (int) Math.round((ordered[middle - 1] + ordered[middle]) * 0.5)
                : ordered[middle];
    }

    private static int roundedMean(List<Integer> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        long sum = values.stream().mapToLong(Integer::longValue).sum();
        return (int) Math.round(sum / (double) values.size());
    }

    record Observation(long cueId, long cycleIndex, int cueIndex,
                       int cuesPerCycle, int errorMillis, boolean coarseTiming) {
        Observation {
            if (cueId < 0L || cycleIndex < 0L || cueIndex < 0
                    || cuesPerCycle < 1 || cueIndex >= cuesPerCycle) {
                throw new IllegalArgumentException("invalid calibration cue identity");
            }
        }
    }

    enum SampleResult {
        ACCEPTED,
        COMPLETE,
        ADAPTING,
        DUPLICATE,
        OUT_OF_RANGE
    }

    record Estimate(int offsetMillis, int medianDeviationMillis,
                    int inlierCount, int observationCount,
                    int confidenceRadiusMillis, int driftMillis,
                    double effectiveCycleCount, int recentStableCycles,
                    boolean withinSupportedRange) {
        boolean reliable() {
            return withinSupportedRange
                    && inlierCount >= REQUIRED_SAMPLES
                    && effectiveCycleCount >= MINIMUM_EFFECTIVE_CYCLES
                    && recentStableCycles >= MINIMUM_RECENT_STABLE_CYCLES
                    && medianDeviationMillis <= MAXIMUM_RELIABLE_DEVIATION_MILLIS
                    && confidenceRadiusMillis <= MAXIMUM_CONFIDENCE_RADIUS_MILLIS
                    && driftMillis <= MAXIMUM_STABLE_DRIFT_MILLIS;
        }
    }

    private record CycleSample(long cycleIndex, int centerMillis,
                               int deviationMillis, double coverage,
                               int sampleCount, boolean eligible) {
    }

    private record WeightedCycle(CycleSample sample, double weight,
                                 int stableRun) {
    }

    private static final class WeightedDeviation {
        private final int deviation;
        private final double weight;

        private WeightedDeviation(int deviation, double weight) {
            this.deviation = deviation;
            this.weight = weight;
        }

        private int deviation() {
            return deviation;
        }

        private double weight() {
            return weight;
        }
    }

    private record Analysis(Estimate estimate, int latestStableRun) {
    }
}
