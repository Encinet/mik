package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmLatencyCompensatorTest {

    @Test
    void rewindsByTheRoundTripTakenByPresentationAndReturningInput() {
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(120);

        assertEquals(1_880L, latency.inputPosition(2_000L));
        assertEquals(120, latency.compensationMillis());
    }

    @Test
    void givesAutomaticMissesAnExtraTickAndJitterGrace() {
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(80);

        assertEquals(895L, latency.missPosition(1_000L));
        latency.observe(500);

        assertEquals(80, latency.compensationMillis(),
                "one ping spike must not move the input judgement clock");
        assertTrue(latency.missGraceMillis() > 25,
                "a spike may safely delay automatic misses");
    }

    @Test
    void followsARealSustainedLatencyChangeWithoutOneSampleJumps() {
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(60);

        latency.observe(260);
        assertEquals(60, latency.compensationMillis());
        for (int sample = 0; sample < 16; sample++) latency.observe(200);

        assertTrue(latency.compensationMillis() >= 185);
        assertTrue(latency.compensationMillis() <= 205);
    }

    @Test
    void runtimeSamplingCannotFillTheMedianWithOneRepeatedPingReading() {
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(60, 0, 0L);

        for (long tick = 1L; tick < 20L; tick++) {
            assertTrue(!latency.sample(260, tick * 50_000_000L));
        }
        assertEquals(60, latency.compensationMillis());

        for (int second = 1; second <= 16; second++) {
            assertTrue(latency.sample(200,
                    second * RhythmLatencyCompensator.SAMPLE_INTERVAL_NANOS));
        }
        assertTrue(latency.compensationMillis() >= 185);
        assertTrue(latency.compensationMillis() <= 205);
    }

    @Test
    void clampsUnusablePingValuesAndNeverCreatesNegativeSongTime() {
        assertEquals(0, new RhythmLatencyCompensator(-30).compensationMillis());
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(5_000);

        assertEquals(RhythmLatencyCompensator.MAXIMUM_RTT_MILLIS,
                latency.compensationMillis());
        assertEquals(0L, latency.inputPosition(200L));
        assertEquals(0L, latency.missPosition(200L));
    }

    @Test
    void compensatedClockFeedsTheSharedJudgementSession() {
        RhythmTimeline timeline = new RhythmTimeline("latency");
        timeline.append(new RhythmPulse(1_000L, 0.8));
        timeline.markComplete(1_500L);
        RhythmChartView chart = new RhythmChartView(
                timeline, RhythmDifficulty.NORMAL);
        RhythmCue cue = chart.between(1_000L, 1_000L).getFirst();
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0L, RhythmDifficulty.NORMAL);
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(140);

        RhythmGameSession.Result result = session.input(cue.input(),
                latency.inputPosition(1_140L), chart);

        assertEquals(RhythmJudgement.PERFECT, result.judgement());
        assertEquals(0L, result.timingErrorMillis());
    }

    @Test
    void combinesSavedCalibrationWithLiveNetworkLatency() {
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(120, 45);

        assertEquals(1_835L, latency.inputPosition(2_000L));
        assertEquals(1_880L, latency.networkAdjustedPosition(2_000L));
        assertEquals(1_810L, latency.missPosition(2_000L));
        assertEquals(45, latency.calibrationOffsetMillis());
        assertEquals(165, latency.totalCompensationMillis());

        for (int sample = 0; sample < 16; sample++) latency.observe(200);

        assertTrue(latency.compensationMillis() >= 185);
        assertEquals(45, latency.calibrationOffsetMillis(),
                "network adaptation must not rewrite the device calibration");
        assertTrue(latency.totalCompensationMillis() >= 230);
    }

    @Test
    void appliesAnimationAlignmentWithoutChangingTheJudgementClock() {
        RhythmLatencyCompensator latency =
                new RhythmLatencyCompensator(120, 80, 65);

        assertEquals(1_800L, latency.inputPosition(2_000L));
        assertEquals(1_985L, latency.visualPosition(2_000L, 50L));
        assertEquals(1_815L, latency.visualNetworkPosition(2_000L));
        assertEquals(65, latency.animationOffsetMillis());
    }

    @Test
    void negativeAnimationAlignmentSafelyAdvancesTheScene() {
        RhythmLatencyCompensator latency =
                new RhythmLatencyCompensator(20, 0, -80);

        assertEquals(1_130L, latency.visualPosition(1_000L, 50L));
        assertEquals(Long.MAX_VALUE,
                latency.visualPosition(Long.MAX_VALUE, 50L));
    }

    @Test
    void sharedSessionStaysAlignedWhenLivePingChangesAfterCalibration() {
        RhythmTimeline timeline = new RhythmTimeline("dynamic-latency");
        timeline.append(new RhythmPulse(1_000L, 0.8));
        timeline.append(new RhythmPulse(2_000L, 0.9));
        timeline.markComplete(2_500L);
        RhythmChartView chart = new RhythmChartView(
                timeline, RhythmDifficulty.NORMAL);
        RhythmCue first = chart.between(1_000L, 1_000L).getFirst();
        RhythmCue second = chart.between(2_000L, 2_000L).getFirst();
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0L, RhythmDifficulty.NORMAL);
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(60, 45);

        RhythmGameSession.Result firstResult = session.input(first.input(),
                latency.inputPosition(1_105L), chart);
        for (int sample = 0; sample < 16; sample++) latency.observe(210);
        int updatedNetworkDelay = latency.compensationMillis();
        RhythmGameSession.Result secondResult = session.input(second.input(),
                latency.inputPosition(2_000L + updatedNetworkDelay + 45L), chart);

        assertEquals(RhythmJudgement.PERFECT, firstResult.judgement());
        assertTrue(updatedNetworkDelay >= 195,
                "the rolling clock should follow a sustained RTT increase");
        assertEquals(RhythmJudgement.PERFECT, secondResult.judgement());
        assertEquals(2, session.view().combo());
    }

    @Test
    void supportsANegativeCalibrationWithoutOverflowingSongTime() {
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(20, -80);

        assertEquals(1_060L, latency.inputPosition(1_000L));
        assertEquals(Long.MAX_VALUE,
                latency.inputPosition(Long.MAX_VALUE));
    }
}
