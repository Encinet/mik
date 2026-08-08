package org.encinet.mik.module.music.rhythm.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmBeatTrackerTest {

    @Test
    void locksToTheFullSongPulseAndRejectsOffBeatDecoration() {
        List<RhythmPulse> candidates = new ArrayList<>();
        List<RhythmPulse> anchors = new ArrayList<>();
        for (long time = 1_000L; time <= 8_000L; time += 500L) {
            double strength = time % 1_000L == 0L ? 0.82 : 0.27;
            candidates.add(pulse(time, strength));
            candidates.add(pulse(time + 175L, 0.31));
            if (time % 1_000L == 0L) anchors.add(pulse(time, strength));
        }

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, anchors, 9_000L);

        for (long expected = 1_000L; expected <= 8_000L; expected += 500L) {
            assertNear(refined, expected, 25L);
        }
        assertTrue(refined.stream().noneMatch(pulse -> pulse.timeMillis() % 500L
                >= 150L && pulse.timeMillis() % 500L <= 210L),
                () -> "off-grid pulses=" + times(refined));
    }

    @Test
    void recoversOneWeakBeatFromItsNeighborsAndGlobalPhase() {
        List<RhythmPulse> candidates = new ArrayList<>();
        List<RhythmPulse> anchors = new ArrayList<>();
        for (long time = 500L; time <= 6_000L; time += 500L) {
            if (time != 3_000L) candidates.add(pulse(time, 0.48));
            if (time % 1_500L == 0L && time != 3_000L) {
                anchors.add(pulse(time, 0.78));
            }
        }

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, anchors, 6_500L);

        RhythmPulse recovered = nearest(refined, 3_000L);
        assertTrue(Math.abs(recovered.timeMillis() - 3_000L) <= 25L,
                () -> "pulses=" + times(refined));
        assertTrue(recovered.strength() < 0.48,
                "an inferred beat must remain visually less prominent");
    }

    @Test
    void reestimatesTempoAcrossLongSongRegions() {
        List<RhythmPulse> candidates = new ArrayList<>();
        List<RhythmPulse> anchors = new ArrayList<>();
        addRun(candidates, anchors, 500L, 10_500L, 500L);
        addRun(candidates, anchors, 10_900L, 21_300L, 400L);

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, anchors, 22_000L);

        assertCoverage(refined, 500L, 10_000L, 500L, 0.80);
        assertCoverage(refined, 11_300L, 21_300L, 400L, 0.80);
    }

    @Test
    void followsSparseVerseAndDenseChorusInsteadOfUsingOneFixedEmitterRate() {
        List<RhythmPulse> candidates = new ArrayList<>();
        List<RhythmPulse> anchors = new ArrayList<>();
        int index = 0;
        for (long time = 500L; time <= 9_500L; time += 1_000L) {
            candidates.add(pulse(time, 0.55));
            if (index++ % 2 == 0) anchors.add(pulse(time, 0.74));
        }
        index = 0;
        for (long time = 10_000L; time <= 19_500L; time += 500L) {
            candidates.add(pulse(time, 0.50));
            if (index++ % 4 == 0) anchors.add(pulse(time, 0.72));
        }

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, anchors, 20_000L);

        long verseCount = refined.stream().filter(pulse ->
                pulse.timeMillis() >= 500L && pulse.timeMillis() < 9_500L).count();
        long chorusCount = refined.stream().filter(pulse ->
                pulse.timeMillis() >= 10_500L && pulse.timeMillis() < 19_500L).count();
        assertTrue(verseCount >= 8 && verseCount <= 10,
                () -> "verse pulses=" + times(refined));
        assertTrue(chorusCount >= 16,
                () -> "chorus pulses=" + times(refined));
        assertTrue(chorusCount > verseCount * 1.6,
                "the denser section must produce a denser chart region");
    }

    @Test
    void preservesFastEighthNotesInsteadOfLockingToHalfTime() {
        List<RhythmPulse> candidates = new ArrayList<>();
        List<RhythmPulse> anchors = new ArrayList<>();
        int index = 0;
        for (long time = 500L; time <= 6_000L; time += 250L) {
            candidates.add(pulse(time, index % 2 == 0 ? 0.68 : 0.44));
            if (index % 2 == 0) anchors.add(pulse(time, 0.74));
            index++;
        }

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, anchors, 6_500L);

        assertCoverage(refined, 500L, 6_000L, 250L, 0.90);
        assertTrue(refined.size() >= 21,
                () -> "half-time output=" + times(refined));
    }

    @Test
    void preservesFastTwoHundredMillisecondElectronicPulse() {
        List<RhythmPulse> candidates = new ArrayList<>();
        List<RhythmPulse> anchors = new ArrayList<>();
        int index = 0;
        for (long time = 400L; time <= 5_600L; time += 200L) {
            candidates.add(pulse(time, index % 4 == 0 ? 0.75 : 0.47));
            if (index % 4 == 0) anchors.add(pulse(time, 0.78));
            index++;
        }

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, anchors, 6_000L);

        assertCoverage(refined, 400L, 5_600L, 200L, 0.88);
    }

    @Test
    void keepsHumanizedAttackTimesInsteadOfQuantizingThemToTheGrid() {
        int[] jitter = {0, 18, -23, 11, -16, 25, -8, 14};
        List<RhythmPulse> candidates = new ArrayList<>();
        List<RhythmPulse> anchors = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            long expected = 700L + index * 500L;
            long audibleAttack = expected + jitter[index % jitter.length];
            candidates.add(pulse(audibleAttack, 0.50));
            if (index % 3 == 0) anchors.add(pulse(audibleAttack, 0.76));
        }

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, anchors, 9_000L);

        for (RhythmPulse attack : candidates) {
            assertTrue(refined.stream().anyMatch(pulse ->
                            pulse.timeMillis() == attack.timeMillis()),
                    () -> "quantized or missing attack " + attack.timeMillis()
                            + " in " + times(refined));
        }
    }

    @Test
    void doesNotDoubleASlowSixtyBpmPulse() {
        List<RhythmPulse> beats = new ArrayList<>();
        for (long time = 1_000L; time <= 9_000L; time += 1_000L) {
            beats.add(pulse(time, 0.72));
        }

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                beats, beats, 10_000L);

        assertEquals(times(beats), times(refined));
    }

    @Test
    void doesNotInventABeatGridForSparseNonPeriodicNoise() {
        List<RhythmPulse> candidates = List.of(
                pulse(310L, 0.25), pulse(890L, 0.31), pulse(1_760L, 0.28),
                pulse(2_390L, 0.24), pulse(3_340L, 0.29),
                pulse(4_170L, 0.27), pulse(5_460L, 0.30));

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, List.of(), 6_000L);

        assertEquals(List.of(), refined);
    }

    @Test
    void preservesConservativeAccentsWhenThereIsNotEnoughTempoEvidence() {
        List<RhythmPulse> anchors = List.of(
                pulse(420L, 0.72), pulse(1_130L, 0.66), pulse(2_070L, 0.81));

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                anchors, anchors, 2_500L);

        assertEquals(times(anchors), times(refined));
    }

    @Test
    void strongSyncopatedCandidatesRemainPlayableWithoutConservativeAnchors() {
        List<RhythmPulse> candidates = List.of(
                pulse(280L, 0.58), pulse(690L, 0.48), pulse(1_140L, 0.55),
                pulse(1_670L, 0.46), pulse(2_210L, 0.61), pulse(2_860L, 0.50));

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, List.of(), 3_200L);

        assertEquals(times(candidates), times(refined));
    }

    @Test
    void lowSalienceNonPeriodicCandidatesStillDoNotBecomeAFallbackChart() {
        List<RhythmPulse> candidates = List.of(
                pulse(180L, 0.25), pulse(470L, 0.29), pulse(910L, 0.28),
                pulse(1_260L, 0.31), pulse(1_880L, 0.27), pulse(2_170L, 0.30),
                pulse(2_780L, 0.26), pulse(3_130L, 0.29), pulse(3_940L, 0.28),
                pulse(4_260L, 0.30), pulse(5_090L, 0.27), pulse(5_480L, 0.31));

        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates, List.of(), 6_000L);

        assertEquals(List.of(), refined);
    }

    private static void addRun(List<RhythmPulse> candidates,
                               List<RhythmPulse> anchors, long start,
                               long end, long period) {
        int index = 0;
        for (long time = start; time <= end; time += period) {
            candidates.add(pulse(time, index % 2 == 0 ? 0.62 : 0.34));
            if (index % 4 == 0) anchors.add(pulse(time, 0.74));
            index++;
        }
    }

    private static void assertCoverage(List<RhythmPulse> pulses, long start,
                                       long end, long period,
                                       double minimumCoverage) {
        int expected = 0;
        int matched = 0;
        for (long time = start; time <= end; time += period) {
            expected++;
            if (Math.abs(nearest(pulses, time).timeMillis() - time) <= 55L) matched++;
        }
        int actualMatched = matched;
        int actualExpected = expected;
        assertTrue((double) matched / expected >= minimumCoverage,
                () -> "coverage=" + actualMatched + "/" + actualExpected
                        + ", pulses=" + times(pulses));
    }

    private static void assertNear(List<RhythmPulse> pulses, long expected,
                                   long tolerance) {
        assertTrue(Math.abs(nearest(pulses, expected).timeMillis() - expected)
                        <= tolerance,
                () -> "missing " + expected + " in " + times(pulses));
    }

    private static RhythmPulse nearest(List<RhythmPulse> pulses, long expected) {
        return pulses.stream().min(java.util.Comparator.comparingLong(
                        pulse -> Math.abs(pulse.timeMillis() - expected)))
                .orElseThrow(() -> new AssertionError("No rhythm pulses"));
    }

    private static List<Long> times(List<RhythmPulse> pulses) {
        return pulses.stream().map(RhythmPulse::timeMillis).toList();
    }

    private static RhythmPulse pulse(long timeMillis, double strength) {
        return new RhythmPulse(timeMillis, strength, 0.0, 0.0, timeMillis * 31L);
    }
}
