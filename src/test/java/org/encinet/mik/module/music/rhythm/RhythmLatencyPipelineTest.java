package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic simulations of the complete tap-to-judgement latency path. */
class RhythmLatencyPipelineTest {

    @Test
    void musicTapCalibrationProducesZeroErrorAtEveryDifficulty() {
        int actualRttMillis = 140;
        int deviceOffsetMillis = 65;
        RhythmLatencyCompensator calibrationClock =
                new RhythmLatencyCompensator(actualRttMillis);
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        recordCycles(calibration, calibrationClock, actualRttMillis,
                deviceOffsetMillis);

        int savedOffset = calibration.estimate().offsetMillis();
        assertEquals(deviceOffsetMillis, savedOffset);
        for (RhythmDifficulty difficulty : RhythmDifficulty.values()) {
            RhythmTimeline timeline = timelineAt(7_000L);
            RhythmChartView chart = new RhythmChartView(timeline, difficulty);
            RhythmCue playable = chart.between(7_000L, 7_000L).getFirst();
            RhythmGameSession session = new RhythmGameSession(
                    UUID.randomUUID(), 0L, difficulty);
            RhythmLatencyCompensator gameClock =
                    new RhythmLatencyCompensator(actualRttMillis, savedOffset);
            long packetArrival = playable.timeMillis()
                    + actualRttMillis + deviceOffsetMillis;

            RhythmGameSession.Result result = session.input(playable.input(),
                    gameClock.inputPosition(packetArrival), chart);

            assertEquals(RhythmJudgement.PERFECT, result.judgement(),
                    difficulty.name());
            assertEquals(0L, result.timingErrorMillis(), difficulty.name());
        }
    }

    @Test
    void liveRttChangesWithoutRewritingTheFixedMusicOffset() {
        int fixedOffsetMillis = 65;
        int currentRttMillis = 200;
        RhythmLatencyCompensator clock =
                new RhythmLatencyCompensator(60, fixedOffsetMillis);
        for (int sample = 0; sample < 16; sample++) {
            clock.observe(currentRttMillis);
        }
        RhythmTimeline timeline = timelineAt(1_000L);
        RhythmChartView chart = new RhythmChartView(
                timeline, RhythmDifficulty.EXPERT);
        RhythmCue cue = chart.between(1_000L, 1_000L).getFirst();
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0L, RhythmDifficulty.EXPERT);
        long packetArrival = cue.timeMillis()
                + currentRttMillis + fixedOffsetMillis;

        RhythmGameSession.Result result = session.input(cue.input(),
                clock.inputPosition(packetArrival), chart);

        assertEquals(fixedOffsetMillis, clock.calibrationOffsetMillis());
        assertTrue(clock.compensationMillis() >= 185);
        assertEquals(RhythmJudgement.PERFECT, result.judgement());
        assertTrue(Math.abs(result.timingErrorMillis()) <= 15L);
    }

    @Test
    void automaticMissWaitsForTheLatestCalibratedPacket() {
        RhythmDifficulty difficulty = RhythmDifficulty.EXPERT;
        RhythmTimeline timeline = timelineAt(1_000L);
        RhythmChartView chart = new RhythmChartView(timeline, difficulty);
        RhythmCue cue = chart.between(1_000L, 1_000L).getFirst();
        RhythmLatencyCompensator clock = new RhythmLatencyCompensator(160, 70);
        long latestLegalPacketArrival = cue.timeMillis()
                + difficulty.goodWindowMillis()
                + clock.totalCompensationMillis();
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0L, difficulty);

        int prematureMisses = session.advance(
                clock.missPosition(latestLegalPacketArrival), chart);
        RhythmGameSession.Result result = session.input(cue.input(),
                clock.inputPosition(latestLegalPacketArrival), chart);

        assertEquals(0, prematureMisses);
        assertEquals(RhythmJudgement.GOOD, result.judgement());
        assertEquals(difficulty.goodWindowMillis(), result.timingErrorMillis());
    }

    @Test
    void recalibrationDoesNotFeedTheOldSavedOffsetBackIntoTheMeasurement() {
        RhythmLatencyCompensator oldClock = new RhythmLatencyCompensator(100, 90);
        RhythmLatencyCalibration replacement = new RhythmLatencyCalibration();
        recordCycles(replacement, oldClock, 100, 35);

        assertEquals(35, replacement.estimate().offsetMillis());
    }

    @Test
    void radialGeometryUsesNetworkAndVisualDelayWhileScoringUsesTapDelay() {
        RhythmLatencyCompensator clock =
                new RhythmLatencyCompensator(120, 80, 35);
        long packetArrivalPosition = 2_200L;

        long visualAimPosition = clock.visualNetworkPosition(packetArrivalPosition);
        long judgementPosition = clock.inputPosition(packetArrivalPosition);

        assertEquals(2_045L, visualAimPosition,
                "aim reconstruction must match the calibrated scene shown to the player");
        assertEquals(2_000L, judgementPosition);
    }

    private static RhythmTimeline timelineAt(long timeMillis) {
        RhythmTimeline timeline = new RhythmTimeline("latency-pipeline");
        timeline.append(new RhythmPulse(timeMillis, 0.9));
        timeline.markComplete(timeMillis + 500L);
        return timeline;
    }

    private static RhythmCue cue(long id, long timeMillis) {
        return new RhythmCue(id, timeMillis, RhythmInput.ONE, 0.9);
    }

    private static void recordCycles(RhythmLatencyCalibration calibration,
                                     RhythmLatencyCompensator clock,
                                     int rttMillis, int fixedOffsetMillis) {
        int cuesPerCycle = 6;
        for (int cycle = 0; cycle < 4; cycle++) {
            for (int cueIndex = 0; cueIndex < cuesPerCycle; cueIndex++) {
                long cueTime = 1_000L + (long) cycle * 6_000L
                        + cueIndex * 800L;
                long packetArrival = cueTime + rttMillis + fixedOffsetMillis;
                long adjusted = clock.networkAdjustedPosition(packetArrival);
                int error = Math.toIntExact(adjusted - cueTime);
                long cueId = (long) cycle * cuesPerCycle + cueIndex + 1L;
                calibration.record(new RhythmLatencyCalibration.Observation(
                        cueId, cycle, cueIndex, cuesPerCycle, error, false));
            }
            calibration.advanceToCycle(cycle + 1L);
        }
    }
}
