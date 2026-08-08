package org.encinet.mik.module.music.rhythm.analysis;

/**
 * Streaming multi-band onset detector fed by Lavaplayer's decoded PCM.
 *
 * <p>The detector uses half-wave rectified log-energy flux, a causal adaptive
 * threshold, one-frame local-maximum peak picking, and a refractory interval.
 * This avoids treating steady loud passages as repeated beats while remaining
 * cheap enough to run inside the existing decoder pipeline.</p>
 */
final class RhythmOnsetDetector implements PcmRhythmExtractor {
    private static final long WINDOW_MILLIS = 20L;
    private static final long HISTORY_MILLIS = 1_200L;
    private static final double[] BAND_CUTOFFS_HZ = {
            70.0, 150.0, 320.0, 700.0, 1_500.0, 3_200.0, 7_000.0
    };
    private static final double[] BAND_WEIGHTS = {
            1.24, 1.18, 1.10, 1.04, 0.98, 0.91, 0.84, 0.78
    };
    private static final int BAND_COUNT = BAND_CUTOFFS_HZ.length + 1;
    private static final double LOG_GAIN = 32.0;
    private static final double MAD_NORMALIZATION = 1.4826;
    private static final double SLOW_ENVELOPE_COEFFICIENT = 0.16;
    private static final double BROADBAND_SMOOTHING_COEFFICIENT = 0.42;
    private static final double BROADBAND_FLOOR_COEFFICIENT = 0.07;
    private static final double NEIGHBOR_TRAJECTORY_WEIGHT = 1.0;
    private static final Profile CONSERVATIVE = new Profile(
            110L, 8, 0.0045, 0.018, 1.5);
    private static final Profile WHOLE_TRACK_ANCHORS = new Profile(
            80L, 5, 0.0015, 0.010, 1.05);
    private static final Profile WHOLE_TRACK_CANDIDATES = new Profile(
            45L, 4, 0.0007, 0.0025, 0.55);

    private final DetectionTarget[] targets;
    private final int sampleRate;
    private final int windowSamples;
    private final double[] filterCoefficients = new double[BAND_CUTOFFS_HZ.length];
    private final double[] bandSquares = new double[BAND_COUNT];
    private final double[] frameBandSquares = new double[BAND_COUNT];
    private final double[] currentBands = new double[BAND_COUNT];
    private final double[] previousBands = new double[BAND_COUNT];
    private final double[] slowBands = new double[BAND_COUNT];
    private final double[] noveltyHistory;
    private long sampleCursor;
    private int accumulatedSamples;
    private int analyzedWindows;
    private double broadbandSquares;
    private double leftSquares;
    private double rightSquares;
    private double previousBroadbandEnergy;
    private double slowBroadbandEnergy;
    private double[][] filterStates = new double[0][0];
    private int noveltyHistorySize;
    private int noveltyHistoryCursor;
    private double noveltyBeforePending;
    private WindowFeature pending;

    RhythmOnsetDetector(RhythmExtractionSink output, int sampleRate,
                        long initialPositionMillis) {
        this(new DetectionTarget[] {
                new DetectionTarget(output, CONSERVATIVE)
        }, sampleRate, initialPositionMillis);
    }

    static RhythmOnsetDetector wholeTrack(RhythmExtractionSink candidateOutput,
                                          RhythmExtractionSink anchorOutput,
                                          int sampleRate,
                                          long initialPositionMillis) {
        return new RhythmOnsetDetector(new DetectionTarget[] {
                new DetectionTarget(candidateOutput, WHOLE_TRACK_CANDIDATES),
                new DetectionTarget(anchorOutput, WHOLE_TRACK_ANCHORS)
        }, sampleRate, initialPositionMillis);
    }

    private RhythmOnsetDetector(DetectionTarget[] targets, int sampleRate,
                                long initialPositionMillis) {
        this.targets = java.util.Arrays.copyOf(
                java.util.Objects.requireNonNull(targets, "targets"), targets.length);
        if (this.targets.length == 0) {
            throw new IllegalArgumentException("at least one detection target is required");
        }
        for (DetectionTarget target : this.targets) {
            java.util.Objects.requireNonNull(target, "target");
        }
        if (sampleRate < 1) throw new IllegalArgumentException("sample rate must be positive");
        this.sampleRate = sampleRate;
        this.windowSamples = Math.max(1,
                (int) Math.round(sampleRate * WINDOW_MILLIS / 1000.0));
        this.noveltyHistory = new double[Math.max(8,
                (int) Math.round(HISTORY_MILLIS / (double) WINDOW_MILLIS))];
        for (int index = 0; index < filterCoefficients.length; index++) {
            filterCoefficients[index] = lowPassCoefficient(
                    BAND_CUTOFFS_HZ[index], sampleRate);
        }
        seek(initialPositionMillis);
    }

    @Override
    public void accept(float[][] channels, int offset, int length) {
        if (channels == null || channels.length == 0 || length <= 0 || offset < 0) return;
        int end = offset + length;
        ensureChannelState(channels.length);
        for (int sample = offset; sample < end; sample++) {
            double left = 0.0;
            double right = 0.0;
            double frameBroadbandSquares = 0.0;
            java.util.Arrays.fill(frameBandSquares, 0.0);
            int availableChannels = 0;
            for (int channelIndex = 0; channelIndex < channels.length; channelIndex++) {
                float[] channel = channels[channelIndex];
                if (channel == null || sample >= channel.length) continue;
                double value = channel[sample];
                if (availableChannels == 0) left = value;
                if (availableChannels == 1) right = value;
                double lowerLowPass = 0.0;
                for (int filter = 0; filter < filterCoefficients.length; filter++) {
                    double state = filterStates[channelIndex][filter];
                    state += filterCoefficients[filter] * (value - state);
                    filterStates[channelIndex][filter] = state;
                    double band = state - lowerLowPass;
                    frameBandSquares[filter] += band * band;
                    lowerLowPass = state;
                }
                double highestBand = value - lowerLowPass;
                frameBandSquares[BAND_COUNT - 1] += highestBand * highestBand;
                frameBroadbandSquares += value * value;
                availableChannels++;
            }
            if (availableChannels == 0) continue;
            if (availableChannels == 1) right = left;
            broadbandSquares += frameBroadbandSquares / availableChannels;
            for (int band = 0; band < BAND_COUNT; band++) {
                bandSquares[band] += frameBandSquares[band] / availableChannels;
            }
            leftSquares += left * left;
            rightSquares += right * right;
            accumulatedSamples++;
            sampleCursor++;
            if (accumulatedSamples >= windowSamples) evaluateWindow();
        }
    }

    @Override
    public void seek(long positionMillis) {
        sampleCursor = Math.max(0L, positionMillis) * sampleRate / 1000L;
        accumulatedSamples = 0;
        analyzedWindows = 0;
        broadbandSquares = 0.0;
        java.util.Arrays.fill(bandSquares, 0.0);
        java.util.Arrays.fill(frameBandSquares, 0.0);
        leftSquares = 0.0;
        rightSquares = 0.0;
        for (double[] states : filterStates) java.util.Arrays.fill(states, 0.0);
        java.util.Arrays.fill(previousBands, 0.0);
        java.util.Arrays.fill(slowBands, 0.0);
        java.util.Arrays.fill(noveltyHistory, 0.0);
        previousBroadbandEnergy = 0.0;
        slowBroadbandEnergy = 0.0;
        noveltyHistorySize = 0;
        noveltyHistoryCursor = 0;
        noveltyBeforePending = 0.0;
        pending = null;
        for (DetectionTarget target : targets) target.reset(positionMillis);
    }

    @Override
    public void flush() {
        if (accumulatedSamples > 0) evaluateWindow();
        emitPendingIfPeak(0.0);
        pending = null;
    }

    private void evaluateWindow() {
        double broadbandRms = Math.sqrt(broadbandSquares / accumulatedSamples);
        double rawBroadbandEnergy = Math.log1p(LOG_GAIN * broadbandRms);
        double broadbandEnergy = analyzedWindows == 0 ? rawBroadbandEnergy
                : previousBroadbandEnergy + BROADBAND_SMOOTHING_COEFFICIENT
                        * (rawBroadbandEnergy - previousBroadbandEnergy);
        for (int band = 0; band < BAND_COUNT; band++) {
            currentBands[band] = logEnergy(bandSquares[band]);
        }
        double novelty = analyzedWindows == 0 ? 0.0
                : novelty(currentBands, broadbandEnergy);
        if (analyzedWindows == 0) {
            System.arraycopy(currentBands, 0, slowBands, 0, BAND_COUNT);
            slowBroadbandEnergy = broadbandEnergy;
        }
        System.arraycopy(currentBands, 0, previousBands, 0, BAND_COUNT);
        previousBroadbandEnergy = broadbandEnergy;

        RobustStatistics statistics = robustStatistics();
        double leftRms = Math.sqrt(leftSquares / accumulatedSamples);
        double rightRms = Math.sqrt(rightSquares / accumulatedSamples);
        double stereoBalance = (rightRms - leftRms)
                / Math.max(1.0E-7, rightRms + leftRms);
        double toneBalance = toneBalance(currentBands);
        long endMillis = sampleCursor * 1000L / sampleRate;
        long windowMillis = Math.max(1L,
                Math.round(accumulatedSamples * 1000.0 / sampleRate));
        long centerMillis = Math.max(0L, endMillis - windowMillis / 2L);
        WindowFeature current = new WindowFeature(centerMillis, novelty,
                statistics.median(), statistics.deviation(),
                broadbandRms, stereoBalance, toneBalance,
                signature(centerMillis, currentBands));

        emitPendingIfPeak(current.novelty());

        if (pending != null) noveltyBeforePending = pending.novelty();
        pending = current;
        updateHistory(novelty);
        analyzedWindows++;

        // Cues are published before the watermark, so readers never observe a
        // supposedly finalized range whose last peak is not visible yet.
        for (DetectionTarget target : targets) {
            target.output().advanceAnalyzedThrough(endMillis);
        }
        resetWindow();
    }

    private void emitPendingIfPeak(double nextNovelty) {
        if (pending == null
                || pending.novelty() < noveltyBeforePending
                || pending.novelty() < nextNovelty) return;
        for (DetectionTarget target : targets) target.emit(pending, analyzedWindows);
    }

    private void updateHistory(double novelty) {
        noveltyHistory[noveltyHistoryCursor] = novelty;
        noveltyHistoryCursor = (noveltyHistoryCursor + 1) % noveltyHistory.length;
        noveltyHistorySize = Math.min(noveltyHistory.length, noveltyHistorySize + 1);
    }

    private RobustStatistics robustStatistics() {
        if (noveltyHistorySize == 0) return new RobustStatistics(0.0, 0.0);
        double[] values = java.util.Arrays.copyOf(noveltyHistory, noveltyHistorySize);
        java.util.Arrays.sort(values);
        double median = median(values);
        for (int index = 0; index < values.length; index++) {
            values[index] = Math.abs(values[index] - median);
        }
        java.util.Arrays.sort(values);
        return new RobustStatistics(median, median(values) * MAD_NORMALIZATION);
    }

    private static double median(double[] sorted) {
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0
                ? (sorted[middle - 1] + sorted[middle]) * 0.5
                : sorted[middle];
    }

    private double logEnergy(double sumSquares) {
        double rms = Math.sqrt(sumSquares / accumulatedSamples);
        return Math.log1p(LOG_GAIN * rms);
    }

    /**
     * Coarse log-frequency flux keeps the detector inexpensive while avoiding a
     * single kick- or vocal-specific band split. The previous-frame neighborhood
     * acts like a small trajectory maximum: vibrato moving energy to an adjacent
     * band contributes less than a genuinely new broadband or pitched attack.
     * A slower envelope term restores soft bowed, piano, and vocal attacks that
     * rise over several short frames instead of producing one sharp transient.
     */
    private double novelty(double[] bands, double broadbandEnergy) {
        double directFlux = 0.0;
        double trajectoryFlux = 0.0;
        double slowRise = 0.0;
        double totalWeight = 0.0;
        for (int band = 0; band < BAND_COUNT; band++) {
            double weight = BAND_WEIGHTS[band];
            double trajectoryReference = previousBands[band];
            if (band > 0) {
                trajectoryReference = Math.max(trajectoryReference,
                        previousBands[band - 1] * NEIGHBOR_TRAJECTORY_WEIGHT);
            }
            if (band + 1 < BAND_COUNT) {
                trajectoryReference = Math.max(trajectoryReference,
                        previousBands[band + 1] * NEIGHBOR_TRAJECTORY_WEIGHT);
            }
            directFlux += weight * positiveDifference(
                    bands[band], previousBands[band]);
            trajectoryFlux += weight * positiveDifference(
                    bands[band], trajectoryReference);
            slowRise += weight * positiveDifference(bands[band], slowBands[band]);
            slowBands[band] += SLOW_ENVELOPE_COEFFICIENT
                    * (bands[band] - slowBands[band]);
            totalWeight += weight;
        }
        double normalizedDirect = directFlux / totalWeight;
        double normalizedTrajectory = trajectoryFlux / totalWeight;
        double normalizedSlowRise = slowRise / totalWeight;
        double broadbandFlux = positiveDifference(
                broadbandEnergy, previousBroadbandEnergy);
        double broadbandRise = positiveDifference(
                broadbandEnergy, slowBroadbandEnergy);
        slowBroadbandEnergy += BROADBAND_FLOOR_COEFFICIENT
                * (broadbandEnergy - slowBroadbandEnergy);
        double spectralGate = Math.clamp(
                (broadbandFlux + broadbandRise * 0.35) / 0.018, 0.02, 1.0);
        return (2.55 * (normalizedTrajectory * 0.90 + normalizedDirect * 0.10)
                + 0.20 * normalizedSlowRise) * spectralGate
                + 0.42 * broadbandFlux + 0.26 * broadbandRise;
    }

    private static double toneBalance(double[] bands) {
        double weightedPosition = 0.0;
        double total = 0.0;
        for (int band = 0; band < bands.length; band++) {
            double position = bands.length == 1 ? 0.0
                    : band * 2.0 / (bands.length - 1.0) - 1.0;
            weightedPosition += bands[band] * position;
            total += bands[band];
        }
        return Math.clamp(weightedPosition / Math.max(1.0E-7, total), -1.0, 1.0);
    }

    private void resetWindow() {
        accumulatedSamples = 0;
        broadbandSquares = 0.0;
        java.util.Arrays.fill(bandSquares, 0.0);
        leftSquares = 0.0;
        rightSquares = 0.0;
    }

    private void ensureChannelState(int channels) {
        if (filterStates.length >= channels) return;
        double[][] expanded = new double[channels][BAND_CUTOFFS_HZ.length];
        for (int channel = 0; channel < filterStates.length; channel++) {
            System.arraycopy(filterStates[channel], 0, expanded[channel], 0,
                    filterStates[channel].length);
        }
        filterStates = expanded;
    }

    private static double positiveDifference(double current, double previous) {
        return Math.max(0.0, current - previous);
    }

    private static double lowPassCoefficient(double cutoffHz, int sampleRate) {
        double safeCutoff = Math.min(cutoffHz, sampleRate * 0.42);
        return 1.0 - Math.exp(-2.0 * Math.PI * safeCutoff / sampleRate);
    }

    private static long signature(long timeMillis, double[] bands) {
        long value = timeMillis * 0x9E3779B97F4A7C15L;
        for (double band : bands) {
            value = Long.rotateLeft(value, 17) ^ Double.doubleToLongBits(band);
        }
        return value;
    }

    private record WindowFeature(long timeMillis, double novelty, double median,
                                 double deviation,
                                 double broadbandRms, double stereoBalance,
                                 double toneBalance, long signature) {
    }

    private record RobustStatistics(double median, double deviation) {
    }

    private record Profile(long minimumIntervalMillis, int warmupWindows,
                           double minimumRms, double minimumNovelty,
                           double thresholdMadMultiplier) {
    }

    /** One threshold policy consuming the shared, expensive filter-bank features. */
    private static final class DetectionTarget {
        private final RhythmExtractionSink output;
        private final Profile profile;
        private long lastCueMillis;

        private DetectionTarget(RhythmExtractionSink output, Profile profile) {
            this.output = java.util.Objects.requireNonNull(output, "output");
            this.profile = java.util.Objects.requireNonNull(profile, "profile");
        }

        private RhythmExtractionSink output() {
            return output;
        }

        private void reset(long positionMillis) {
            lastCueMillis = Math.max(0L, positionMillis)
                    - profile.minimumIntervalMillis();
        }

        private void emit(WindowFeature peak, int analyzedWindows) {
            double threshold = peak.median() + profile.minimumNovelty()
                    + profile.thresholdMadMultiplier() * peak.deviation();
            if (analyzedWindows < profile.warmupWindows()
                    || peak.novelty() <= threshold
                    || peak.broadbandRms() < profile.minimumRms()
                    || peak.timeMillis() - lastCueMillis
                    < profile.minimumIntervalMillis()) return;
            double excess = peak.novelty() - threshold;
            double salienceScale = Math.max(0.30,
                    threshold + peak.deviation() * 3.0);
            double normalizedSalience = 1.0 - Math.exp(-excess / salienceScale);
            double strength = Math.clamp(0.20 + normalizedSalience * 0.78,
                    0.12, 0.98);
            output.append(new RhythmPulse(peak.timeMillis(), strength,
                    peak.stereoBalance(), peak.toneBalance(), peak.signature()));
            lastCueMillis = peak.timeMillis();
        }
    }
}
