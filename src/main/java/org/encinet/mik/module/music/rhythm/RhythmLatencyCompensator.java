package org.encinet.mik.module.music.rhythm;

import java.util.Arrays;

/**
 * Stable per-player judgement clock combining live RTT and calibrated fixed delay.
 *
 * <p>A cue first travels from the server to the player and the resulting input
 * travels back, so the server receives a visually timed press roughly one RTT
 * after the cue's server timestamp. A rolling median rejects isolated ping spikes.
 * The separately calibrated offset contains no network delay: calibration removes
 * the RTT measured with every observation. Runtime then adds the rolling live RTT
 * back to that fixed offset. Automatic misses receive an
 * additional jitter and tick-processing grace so they cannot consume a cue while
 * its legitimate input packet is still in flight.</p>
 */
final class RhythmLatencyCompensator {
    static final int MAXIMUM_RTT_MILLIS = 500;
    static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;
    private static final int SAMPLE_COUNT = 9;
    private static final int SERVER_TICK_GRACE_MILLIS = 25;
    private static final int MAXIMUM_JITTER_GRACE_MILLIS = 60;
    private static final double SMOOTHING = 0.30;

    private final int[] samples = new int[SAMPLE_COUNT];
    private final int calibrationOffsetMillis;
    private final int animationOffsetMillis;
    private int cursor;
    private double smoothedRttMillis;
    private long lastTimedSampleNanos;

    RhythmLatencyCompensator(int initialPingMillis) {
        this(initialPingMillis, 0, 0);
    }

    RhythmLatencyCompensator(int initialPingMillis, int calibrationOffsetMillis) {
        this(initialPingMillis, calibrationOffsetMillis, 0);
    }

    RhythmLatencyCompensator(int initialPingMillis, int calibrationOffsetMillis,
                             int animationOffsetMillis) {
        this(initialPingMillis, calibrationOffsetMillis, animationOffsetMillis,
                Long.MIN_VALUE);
    }

    /** Creates a runtime clock whose initial ping already represents this instant. */
    RhythmLatencyCompensator(int initialPingMillis, int calibrationOffsetMillis,
                             long initialSampleNanos) {
        this(initialPingMillis, calibrationOffsetMillis, 0, initialSampleNanos);
    }

    /** Creates a runtime clock with independent judgement and animation offsets. */
    RhythmLatencyCompensator(int initialPingMillis, int calibrationOffsetMillis,
                             int animationOffsetMillis, long initialSampleNanos) {
        int initial = currentNetworkRttMillis(initialPingMillis);
        Arrays.fill(samples, initial);
        smoothedRttMillis = initial;
        this.calibrationOffsetMillis = Math.clamp(calibrationOffsetMillis,
                RhythmLatencyCalibration.MINIMUM_OFFSET_MILLIS,
                RhythmLatencyCalibration.MAXIMUM_OFFSET_MILLIS);
        this.animationOffsetMillis = Math.clamp(animationOffsetMillis,
                RhythmLatencyProfile.MINIMUM_ANIMATION_OFFSET_MILLIS,
                RhythmLatencyProfile.MAXIMUM_ANIMATION_OFFSET_MILLIS);
        this.lastTimedSampleNanos = initialSampleNanos;
    }

    /** Records one already time-separated observation (primarily for deterministic tests). */
    void observe(int pingMillis) {
        samples[cursor] = currentNetworkRttMillis(pingMillis);
        cursor = (cursor + 1) % samples.length;
        double target = percentile(0.50);
        smoothedRttMillis += (target - smoothedRttMillis) * SMOOTHING;
    }

    /**
     * Samples the server's current ping at most once per measurement interval.
     * Input callbacks must only read this clock: repeatedly inserting the same
     * server ping value would otherwise make a momentary value dominate the median.
     */
    boolean sample(int pingMillis, long sampledAtNanos) {
        if (lastTimedSampleNanos != Long.MIN_VALUE) {
            long elapsed = sampledAtNanos - lastTimedSampleNanos;
            if (elapsed < SAMPLE_INTERVAL_NANOS) {
                return false;
            }
        }
        observe(pingMillis);
        lastTimedSampleNanos = sampledAtNanos;
        return true;
    }

    long inputPosition(long serverPlaybackPositionMillis) {
        return adjust(serverPlaybackPositionMillis, totalCompensationMillis());
    }

    /** Runtime clock with only the current rolling network RTT removed. */
    long networkAdjustedPosition(long serverPlaybackPositionMillis) {
        return adjust(serverPlaybackPositionMillis, networkCompensationMillis());
    }

    /** Reconstructs the calibrated scene clock visible when an input packet was sent. */
    long visualNetworkPosition(long serverPlaybackPositionMillis) {
        return adjust(networkAdjustedPosition(serverPlaybackPositionMillis),
                animationOffsetMillis);
    }

    /** Applies device A/V alignment and the fixed one-tick renderer lead. */
    long visualPosition(long serverPlaybackPositionMillis, long renderLeadMillis) {
        long aligned = adjust(serverPlaybackPositionMillis, animationOffsetMillis);
        return saturatedAdd(aligned, Math.max(0L, renderLeadMillis));
    }

    long missPosition(long serverPlaybackPositionMillis) {
        return adjust(serverPlaybackPositionMillis,
                totalCompensationMillis() + missGraceMillis());
    }

    int networkCompensationMillis() {
        return Math.clamp((int) Math.round(smoothedRttMillis),
                0, MAXIMUM_RTT_MILLIS);
    }

    int calibrationOffsetMillis() {
        return calibrationOffsetMillis;
    }

    int animationOffsetMillis() {
        return animationOffsetMillis;
    }

    int totalCompensationMillis() {
        return networkCompensationMillis() + calibrationOffsetMillis;
    }

    int missGraceMillis() {
        int median = (int) Math.round(percentile(0.50));
        int upper = (int) Math.round(percentile(0.90));
        int jitter = Math.clamp(upper - median, 0,
                MAXIMUM_JITTER_GRACE_MILLIS);
        return SERVER_TICK_GRACE_MILLIS + jitter;
    }

    private double percentile(double fraction) {
        int[] ordered = Arrays.copyOf(samples, samples.length);
        Arrays.sort(ordered);
        double index = fraction * (ordered.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        double blend = index - lower;
        return ordered[lower] * (1.0 - blend) + ordered[upper] * blend;
    }

    private static long adjust(long positionMillis, int delayMillis) {
        long position = Math.max(0L, positionMillis);
        if (delayMillis >= 0) return Math.max(0L, position - delayMillis);
        long advance = -(long) delayMillis;
        return position > Long.MAX_VALUE - advance
                ? Long.MAX_VALUE : position + advance;
    }

    private static long saturatedAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    /** Normalizes one current server RTT reading for a calibration observation. */
    static int currentNetworkRttMillis(int pingMillis) {
        return Math.clamp(pingMillis, 0, MAXIMUM_RTT_MILLIS);
    }
}
