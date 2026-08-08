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
    private static final double LOW_CUTOFF_HZ = 180.0;
    private static final double BODY_CUTOFF_HZ = 2_400.0;
    private static final double LOG_GAIN = 32.0;
    private static final double MAD_NORMALIZATION = 1.4826;
    private static final int HISTORY_WINDOWS = 50;
    private static final Profile CONSERVATIVE = new Profile(
            120L, 8, 0.0045, 0.018, 1.5);
    private static final Profile WHOLE_TRACK_ANCHORS = new Profile(
            90L, 5, 0.0015, 0.010, 1.05);
    private static final Profile WHOLE_TRACK_CANDIDATES = new Profile(
            55L, 4, 0.0007, 0.0025, 0.55);

    private final RhythmExtractionSink output;
    private final Profile profile;
    private final int sampleRate;
    private final int windowSamples;
    private final double lowCoefficient;
    private final double bodyCoefficient;
    private long sampleCursor;
    private int accumulatedSamples;
    private int analyzedWindows;
    private double broadbandSquares;
    private double lowSquares;
    private double bodySquares;
    private double highSquares;
    private double leftSquares;
    private double rightSquares;
    private double[] lowStates = new double[0];
    private double[] bodyStates = new double[0];
    private final double[] previousBands = new double[3];
    private final double[] noveltyHistory = new double[HISTORY_WINDOWS];
    private int noveltyHistorySize;
    private int noveltyHistoryCursor;
    private double noveltyBeforePending;
    private WindowFeature pending;
    private long lastCueMillis = Long.MIN_VALUE / 4L;

    RhythmOnsetDetector(RhythmExtractionSink output, int sampleRate,
                        long initialPositionMillis) {
        this(output, sampleRate, initialPositionMillis, CONSERVATIVE);
    }

    static RhythmOnsetDetector wholeTrackCandidates(RhythmExtractionSink output,
                                                     int sampleRate,
                                                     long initialPositionMillis) {
        return new RhythmOnsetDetector(output, sampleRate, initialPositionMillis,
                WHOLE_TRACK_CANDIDATES);
    }

    static RhythmOnsetDetector wholeTrackAnchors(RhythmExtractionSink output,
                                                  int sampleRate,
                                                  long initialPositionMillis) {
        return new RhythmOnsetDetector(output, sampleRate, initialPositionMillis,
                WHOLE_TRACK_ANCHORS);
    }

    private RhythmOnsetDetector(RhythmExtractionSink output, int sampleRate,
                                long initialPositionMillis, Profile profile) {
        this.output = java.util.Objects.requireNonNull(output, "output");
        this.profile = java.util.Objects.requireNonNull(profile, "profile");
        if (sampleRate < 1) throw new IllegalArgumentException("sample rate must be positive");
        this.sampleRate = sampleRate;
        this.windowSamples = Math.max(1,
                (int) Math.round(sampleRate * WINDOW_MILLIS / 1000.0));
        this.lowCoefficient = lowPassCoefficient(LOW_CUTOFF_HZ, sampleRate);
        this.bodyCoefficient = lowPassCoefficient(BODY_CUTOFF_HZ, sampleRate);
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
            double frameLowSquares = 0.0;
            double frameBodySquares = 0.0;
            double frameHighSquares = 0.0;
            int availableChannels = 0;
            for (int channelIndex = 0; channelIndex < channels.length; channelIndex++) {
                float[] channel = channels[channelIndex];
                if (channel == null || sample >= channel.length) continue;
                double value = channel[sample];
                if (availableChannels == 0) left = value;
                if (availableChannels == 1) right = value;
                lowStates[channelIndex] += lowCoefficient
                        * (value - lowStates[channelIndex]);
                bodyStates[channelIndex] += bodyCoefficient
                        * (value - bodyStates[channelIndex]);
                double low = lowStates[channelIndex];
                double body = bodyStates[channelIndex] - lowStates[channelIndex];
                double high = value - bodyStates[channelIndex];
                frameBroadbandSquares += value * value;
                frameLowSquares += low * low;
                frameBodySquares += body * body;
                frameHighSquares += high * high;
                availableChannels++;
            }
            if (availableChannels == 0) continue;
            if (availableChannels == 1) right = left;
            broadbandSquares += frameBroadbandSquares / availableChannels;
            lowSquares += frameLowSquares / availableChannels;
            bodySquares += frameBodySquares / availableChannels;
            highSquares += frameHighSquares / availableChannels;
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
        lowSquares = 0.0;
        bodySquares = 0.0;
        highSquares = 0.0;
        leftSquares = 0.0;
        rightSquares = 0.0;
        java.util.Arrays.fill(lowStates, 0.0);
        java.util.Arrays.fill(bodyStates, 0.0);
        java.util.Arrays.fill(previousBands, 0.0);
        java.util.Arrays.fill(noveltyHistory, 0.0);
        noveltyHistorySize = 0;
        noveltyHistoryCursor = 0;
        noveltyBeforePending = 0.0;
        pending = null;
        lastCueMillis = Math.max(0L, positionMillis)
                - profile.minimumIntervalMillis();
    }

    @Override
    public void flush() {
        if (accumulatedSamples > 0) evaluateWindow();
        emitPendingIfPeak(0.0);
        pending = null;
    }

    private void evaluateWindow() {
        double broadbandRms = Math.sqrt(broadbandSquares / accumulatedSamples);
        double[] bands = {
                logEnergy(lowSquares),
                logEnergy(bodySquares),
                logEnergy(highSquares)
        };
        double novelty = 1.20 * positiveDifference(bands[0], previousBands[0])
                + positiveDifference(bands[1], previousBands[1])
                + 0.82 * positiveDifference(bands[2], previousBands[2]);
        System.arraycopy(bands, 0, previousBands, 0, bands.length);

        RobustStatistics statistics = robustStatistics();
        double threshold = statistics.median() + profile.minimumNovelty()
                + profile.thresholdMadMultiplier() * statistics.deviation();
        double leftRms = Math.sqrt(leftSquares / accumulatedSamples);
        double rightRms = Math.sqrt(rightSquares / accumulatedSamples);
        double stereoBalance = (rightRms - leftRms)
                / Math.max(1.0E-7, rightRms + leftRms);
        double toneBalance = (bands[2] - bands[0])
                / Math.max(1.0E-7, bands[0] + bands[1] + bands[2]);
        long endMillis = sampleCursor * 1000L / sampleRate;
        long centerMillis = Math.max(0L, endMillis - WINDOW_MILLIS / 2L);
        WindowFeature current = new WindowFeature(centerMillis, novelty, threshold,
                statistics.deviation(),
                broadbandRms, stereoBalance, toneBalance,
                signature(centerMillis, bands));

        emitPendingIfPeak(current.novelty());

        if (pending != null) noveltyBeforePending = pending.novelty();
        pending = current;
        updateHistory(novelty);
        analyzedWindows++;

        // Cues are published before the watermark, so readers never observe a
        // supposedly finalized range whose last peak is not visible yet.
        output.advanceAnalyzedThrough(endMillis);
        resetWindow();
    }

    private void emitPendingIfPeak(double nextNovelty) {
        if (pending != null
                && analyzedWindows >= profile.warmupWindows()
                && pending.novelty() >= noveltyBeforePending
                && pending.novelty() >= nextNovelty
                && pending.novelty() > pending.threshold()
                && pending.broadbandRms() >= profile.minimumRms()
                && pending.timeMillis() - lastCueMillis
                >= profile.minimumIntervalMillis()) {
            double excess = pending.novelty() - pending.threshold();
            double salienceScale = Math.max(0.30,
                    pending.threshold() + pending.deviation() * 3.0);
            double normalizedSalience = 1.0 - Math.exp(-excess / salienceScale);
            double strength = Math.clamp(0.20 + normalizedSalience * 0.78,
                    0.12, 0.98);
            output.append(new RhythmPulse(pending.timeMillis(), strength,
                    pending.stereoBalance(), pending.toneBalance(), pending.signature()));
            lastCueMillis = pending.timeMillis();
        }
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

    private void resetWindow() {
        accumulatedSamples = 0;
        broadbandSquares = 0.0;
        lowSquares = 0.0;
        bodySquares = 0.0;
        highSquares = 0.0;
        leftSquares = 0.0;
        rightSquares = 0.0;
    }

    private void ensureChannelState(int channels) {
        if (lowStates.length >= channels) return;
        lowStates = java.util.Arrays.copyOf(lowStates, channels);
        bodyStates = java.util.Arrays.copyOf(bodyStates, channels);
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

    private record WindowFeature(long timeMillis, double novelty, double threshold,
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
}
