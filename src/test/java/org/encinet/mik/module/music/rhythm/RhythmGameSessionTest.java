package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmGameSessionTest {

    @Test
    void judgesMatchingMovementAndMaintainsCombo() {
        RhythmTimeline timeline = new RhythmTimeline("song");
        timeline.append(new RhythmPulse(1_000, 0.8));
        timeline.append(new RhythmPulse(1_500, 0.8));
        timeline.markComplete(1_500);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.HARD);
        List<RhythmCue> cues = chart.between(0, 1_500);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.HARD);

        RhythmGameSession.Result first = session.input(
                cues.getFirst().input(), 1_045, chart);
        RhythmGameSession.Result second = session.input(
                cues.get(1).input(), 1_620, chart);

        assertEquals(RhythmJudgement.PERFECT, first.judgement());
        assertEquals(RhythmJudgement.GREAT, second.judgement());
        assertEquals(2, session.view().combo());
        assertEquals(2, session.view().hits());
        assertTrue(session.view().score() > 1_700);
    }

    @Test
    void wrongLaneConsumesNearestCueAndBreaksCombo() {
        RhythmTimeline timeline = new RhythmTimeline("song");
        timeline.append(new RhythmPulse(800, 0.6));
        timeline.markComplete(800);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmCue cue = chart.between(800, 800).getFirst();
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        RhythmGameSession.Result result = session.input(otherThan(cue.input()), 810, chart);

        assertEquals(RhythmJudgement.MISS, result.judgement());
        assertTrue(session.isJudged(cue.id()));
        assertEquals(1, session.view().misses());
        assertEquals(0, session.view().combo());
    }

    @Test
    void advancesOnlyThroughAnalyzedAudio() {
        RhythmTimeline timeline = new RhythmTimeline("stream");
        timeline.append(new RhythmPulse(900, 0.5));
        timeline.advanceAnalyzedThrough(1_300);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        assertEquals(1, session.advance(1_300, chart));
        assertEquals(1, session.view().misses());
        assertEquals(0, session.advance(2_000, chart));
    }

    @Test
    void diagonalPressMatchesItsCorrectLaneBeforeConsideringWrongKeys() {
        RhythmTimeline timeline = new RhythmTimeline("diagonal");
        timeline.append(new RhythmPulse(1_000, 0.7));
        timeline.markComplete(1_000);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmCue cue = chart.between(1_000, 1_000).getFirst();
        RhythmInput wrong = otherThan(cue.input());
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        RhythmGameSession.Result result = session.input(
                List.of(wrong, cue.input()), 1_020, chart);

        assertEquals(RhythmJudgement.PERFECT, result.judgement());
        assertEquals(cue.input(), session.view().lastInput());
        assertEquals(0, session.view().misses());
    }

    @Test
    void entranceDelaySkipsExpiredCuesButKeepsTheCurrentHitWindow() {
        RhythmTimeline timeline = new RhythmTimeline("entrance");
        timeline.append(new RhythmPulse(100, 0.7));
        timeline.append(new RhythmPulse(800, 0.7));
        timeline.markComplete(800);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmCue current = chart.between(800, 800).getFirst();
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        session.beginAt(750);

        assertEquals(0, session.advance(750, chart));
        assertEquals(RhythmJudgement.PERFECT,
                session.input(current.input(), 800, chart).judgement());
        assertEquals(0, session.view().misses());
    }

    @Test
    void cuePublishedTooLateCanBeIgnoredWithoutPenalty() {
        RhythmTimeline timeline = new RhythmTimeline("late");
        timeline.append(new RhythmPulse(1_000, 0.8));
        timeline.markComplete(1_000);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmCue cue = chart.between(1_000, 1_000).getFirst();
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        session.ignore(cue);

        assertEquals(0, session.advance(2_000, chart));
        assertEquals(RhythmJudgement.NONE,
                session.input(cue.input(), 1_000, chart).judgement());
        assertEquals(0, session.view().misses());
    }

    @Test
    void aimedHitJudgesTheSelectedCueWithoutRequiringItsLaneKey() {
        RhythmTimeline timeline = new RhythmTimeline("radial-aim");
        timeline.append(new RhythmPulse(1_000, 0.8));
        timeline.markComplete(1_000);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmCue cue = chart.between(1_000, 1_000).getFirst();
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        RhythmGameSession.Result result = session.hit(cue, 1_070, chart);

        assertEquals(RhythmJudgement.PERFECT, result.judgement());
        assertEquals(cue, result.cue());
        assertEquals(1, session.view().hits());
        assertEquals(0, session.view().misses());
    }

    @Test
    void staleOrForeignAimedCueDoesNotConsumeTheRealCue() {
        RhythmTimeline timeline = new RhythmTimeline("radial-empty-space");
        timeline.append(new RhythmPulse(1_000, 0.8));
        timeline.markComplete(1_000);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmCue real = chart.between(1_000, 1_000).getFirst();
        RhythmCue foreign = new RhythmCue(real.id() + 10L, real.timeMillis(),
                real.input(), real.strength());
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        RhythmGameSession.Result result = session.hit(foreign, 1_000, chart);

        assertEquals(RhythmJudgement.NONE, result.judgement());
        assertEquals(0, session.view().hits());
        assertEquals(0, session.view().misses());
        assertTrue(!session.isJudged(real.id()));
    }

    private static RhythmInput otherThan(RhythmInput input) {
        return input == RhythmInput.ONE ? RhythmInput.TWO : RhythmInput.ONE;
    }
}
