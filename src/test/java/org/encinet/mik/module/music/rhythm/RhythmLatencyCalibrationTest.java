package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmLatencyCalibrationTest {
    private static final int CUES_PER_CYCLE = 6;

    @Test
    void threeLuckyTapsCannotFinishAStage() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        for (int cue = 0; cue < 3; cue++) record(calibration, 0, cue, 40, false);
        calibration.advanceToCycle(1);

        assertTrue(calibration.adapting());
        assertFalse(calibration.complete());
        assertEquals(3, calibration.observationCount());
    }

    @Test
    void stablePartialSecondPhraseFinishesWithoutWaitingForItsBoundary() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        recordCycle(calibration, 0, 39, false);
        assertTrue(calibration.adapting());

        for (int cue = 0; cue < 3; cue++) {
            record(calibration, 1, cue, 42, false);
        }
        assertFalse(calibration.adapting());
        assertFalse(calibration.complete());
        assertEquals(RhythmLatencyCalibration.SampleResult.COMPLETE,
                record(calibration, 1, 3, 41, false));

        assertTrue(calibration.complete());
        assertEquals(10, calibration.observationCount());
        assertTrue(calibration.estimate().effectiveCycleCount() >= 1.0);
        assertTrue(Math.abs(calibration.estimate().offsetMillis() - 40) <= 2);
    }

    @Test
    void straightEightCueMotifAlsoFinishesDuringItsSecondPhrase() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        for (int cue = 0; cue < 8; cue++) {
            record(calibration, 0, cue, 8, 48, false);
        }
        calibration.advanceToCycle(1L);
        for (int cue = 0; cue < 4; cue++) {
            record(calibration, 1, cue, 8, 48, false);
        }

        assertTrue(calibration.complete());
        assertEquals(12, calibration.observationCount());
        assertEquals(48, calibration.estimate().offsetMillis());
    }

    @Test
    void anUnstableCycleIsRetainedButCannotConsumeConfidence() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        recordCycle(calibration, 0, new int[]{-100, 180, -90, 170, -80, 160}, false);
        for (int cycle = 1; cycle <= 5; cycle++) {
            recordCycle(calibration, cycle, 45, false);
        }

        assertTrue(calibration.complete());
        assertEquals(16, calibration.observationCount());
        assertTrue(calibration.rejectedCount() >= CUES_PER_CYCLE);
        assertEquals(45, calibration.estimate().offsetMillis());
    }

    @Test
    void oneOutlierStillConvergesInElevenObservations() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        recordCycle(calibration, 0,
                new int[]{40, 54, 61, 57, 50, 64}, false);
        int[] second = {56, 52, 59, 300, 55};
        for (int cue = 0; cue < second.length; cue++) {
            record(calibration, 1, cue, second[cue], false);
        }

        assertTrue(calibration.complete());
        assertEquals(11, calibration.observationCount());
        assertTrue(Math.abs(calibration.estimate().offsetMillis() - 56) <= 2);
        assertTrue(calibration.estimate().confidenceRadiusMillis()
                <= RhythmLatencyCalibration.MAXIMUM_CONFIDENCE_RADIUS_MILLIS);
        assertTrue(calibration.rejectedCount() >= 1);
    }

    @Test
    void finalCenterUsesEveryFilteredTapInsteadOfOnePhraseCenter() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        recordCycle(calibration, 0, 40, false);
        for (int cue = 0; cue < 4; cue++) {
            record(calibration, 1, cue, 70, false);
        }

        assertTrue(calibration.complete());
        assertEquals(52, calibration.estimate().offsetMillis());
        assertEquals(30, calibration.estimate().driftMillis());
    }

    @Test
    void oldPracticeErrorsAgeOutAndTenStableTapsRecover() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        int[] unstable = {-140, 180, -120, 160, -100, 140};
        for (int cycle = 0; cycle < 8; cycle++) {
            recordCycle(calibration, cycle, unstable, false);
        }
        assertFalse(calibration.complete());

        recordCycle(calibration, 8, 45, false);
        for (int cue = 0; cue < 4; cue++) {
            record(calibration, 9, cue, 45, false);
        }

        assertTrue(calibration.complete());
        assertEquals(45, calibration.estimate().offsetMillis());
        assertEquals(10, calibration.sampleCount());
    }

    @Test
    void aFortyMillisecondPhraseShiftCannotMasqueradeAsStable() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        recordCycle(calibration, 0, 20, false);
        recordCycle(calibration, 1, 60, false);

        assertFalse(calibration.complete());
        assertTrue(calibration.adapting());
    }

    @Test
    void oneBadTapInsideEachPhraseDoesNotBiasTheCycleMedian() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        for (int cycle = 0; cycle < 4; cycle++) {
            recordCycle(calibration, cycle,
                    new int[]{44, 46, 48, 45, 310, 47}, false);
        }

        assertTrue(calibration.complete());
        assertTrue(Math.abs(calibration.estimate().offsetMillis() - 46) <= 1);
        assertTrue(calibration.estimate().medianDeviationMillis() <= 3);
    }

    @Test
    void stableUnsupportedOffsetsAreReportedAndNeverSilentlyClamped() {
        RhythmLatencyCalibration positive = stableCalibration(380, false);
        RhythmLatencyCalibration negative = stableCalibration(-300, false);

        assertFalse(positive.complete());
        assertFalse(negative.complete());
        assertTrue(positive.outOfSupportedRange());
        assertTrue(negative.outOfSupportedRange());
        assertEquals(380, positive.currentEstimate().orElseThrow().offsetMillis());
        assertEquals(-300, negative.currentEstimate().orElseThrow().offsetMillis());
    }

    @Test
    void confidenceKeepsARealTimingResolutionFloor() {
        RhythmLatencyCalibration precise = stableCalibration(50, false);
        RhythmLatencyCalibration coarse = stableCalibration(50, true);

        assertEquals(RhythmLatencyCalibration.PRECISE_UNCERTAINTY_FLOOR_MILLIS,
                precise.estimate().confidenceRadiusMillis());
        assertEquals(RhythmLatencyCalibration.COARSE_UNCERTAINTY_FLOOR_MILLIS,
                coarse.estimate().confidenceRadiusMillis());
    }

    @Test
    void duplicateAndImpossibleObservationsDoNotEnterTheEvidenceLedger() {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        RhythmLatencyCalibration.Observation first = observation(0, 0, 40, false);

        assertEquals(RhythmLatencyCalibration.SampleResult.ADAPTING,
                calibration.record(first));
        assertEquals(RhythmLatencyCalibration.SampleResult.DUPLICATE,
                calibration.record(first));
        assertEquals(RhythmLatencyCalibration.SampleResult.OUT_OF_RANGE,
                calibration.record(new RhythmLatencyCalibration.Observation(
                        99L, 0L, 1, CUES_PER_CYCLE, 451, false)));
        assertEquals(1, calibration.observationCount());
        assertThrows(IllegalArgumentException.class,
                () -> new RhythmLatencyCalibration.Observation(
                        0L, 0L, 0, CUES_PER_CYCLE, 0, false));
    }

    @Test
    void resetOnlyStartsTheIndependentNextModality() {
        RhythmLatencyCalibration calibration = stableCalibration(40, false);
        calibration.reset();

        assertEquals(0, calibration.sampleCount());
        assertEquals(0, calibration.observationCount());
        assertTrue(calibration.adapting());
        assertFalse(calibration.complete());
    }

    private static RhythmLatencyCalibration stableCalibration(int error,
                                                               boolean coarse) {
        RhythmLatencyCalibration calibration = new RhythmLatencyCalibration();
        for (int cycle = 0; cycle < 4; cycle++) {
            recordCycle(calibration, cycle, error, coarse);
        }
        return calibration;
    }

    private static void recordCycle(RhythmLatencyCalibration calibration,
                                    int cycle, int error, boolean coarse) {
        int[] errors = new int[CUES_PER_CYCLE];
        java.util.Arrays.fill(errors, error);
        recordCycle(calibration, cycle, errors, coarse);
    }

    private static void recordCycle(RhythmLatencyCalibration calibration,
                                    int cycle, int[] errors, boolean coarse) {
        for (int cue = 0; cue < errors.length; cue++) {
            record(calibration, cycle, cue, errors[cue], coarse);
        }
        calibration.advanceToCycle(cycle + 1L);
    }

    private static RhythmLatencyCalibration.SampleResult record(
            RhythmLatencyCalibration calibration, int cycle, int cue,
            int error, boolean coarse) {
        return calibration.record(observation(cycle, cue, error, coarse));
    }

    private static RhythmLatencyCalibration.SampleResult record(
            RhythmLatencyCalibration calibration, int cycle, int cue,
            int cuesPerCycle, int error, boolean coarse) {
        long cueId = (long) cycle * cuesPerCycle + cue + 1L;
        return calibration.record(new RhythmLatencyCalibration.Observation(
                cueId, cycle, cue, cuesPerCycle, error, coarse));
    }

    private static RhythmLatencyCalibration.Observation observation(
            int cycle, int cue, int error, boolean coarse) {
        long cueId = (long) cycle * CUES_PER_CYCLE + cue + 1L;
        return new RhythmLatencyCalibration.Observation(cueId, cycle, cue,
                CUES_PER_CYCLE, error, coarse);
    }
}
