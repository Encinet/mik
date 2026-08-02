package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmGameSessionTest {

    @Test
    void judgesMatchingMovementAndMaintainsCombo() {
        RhythmTimeline timeline = new RhythmTimeline("song");
        timeline.append(1_000, RhythmInput.FORWARD, 0.8);
        timeline.append(1_500, RhythmInput.JUMP, 0.8);
        timeline.markAudioComplete(1_500);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.HARD);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.HARD);

        RhythmGameSession.Result first = session.input(
                RhythmInput.FORWARD, 1_045, chart);
        RhythmGameSession.Result second = session.input(
                RhythmInput.JUMP, 1_620, chart);

        assertEquals(RhythmJudgement.PERFECT, first.judgement());
        assertEquals(RhythmJudgement.GREAT, second.judgement());
        assertEquals(2, session.view().combo());
        assertEquals(2, session.view().hits());
        assertTrue(session.view().score() > 1_700);
    }

    @Test
    void wrongLaneConsumesNearestCueAndBreaksCombo() {
        RhythmTimeline timeline = new RhythmTimeline("song");
        RhythmCue cue = timeline.append(800, RhythmInput.LEFT, 0.6);
        timeline.markAudioComplete(800);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        RhythmGameSession.Result result = session.input(
                RhythmInput.RIGHT, 810, chart);

        assertEquals(RhythmJudgement.MISS, result.judgement());
        assertTrue(session.isJudged(cue.id()));
        assertEquals(1, session.view().misses());
        assertEquals(0, session.view().combo());
    }

    @Test
    void advancesOnlyThroughAnalyzedAudio() {
        RhythmTimeline timeline = new RhythmTimeline("stream");
        timeline.append(900, RhythmInput.SNEAK, 0.5);
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
        timeline.append(1_000, RhythmInput.LEFT, 0.7);
        timeline.markAudioComplete(1_000);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        RhythmGameSession.Result result = session.input(
                List.of(RhythmInput.FORWARD, RhythmInput.LEFT), 1_020, chart);

        assertEquals(RhythmJudgement.PERFECT, result.judgement());
        assertEquals(RhythmInput.LEFT, session.view().lastInput());
        assertEquals(0, session.view().misses());
    }

    @Test
    void entranceDelaySkipsExpiredCuesButKeepsTheCurrentHitWindow() {
        RhythmTimeline timeline = new RhythmTimeline("entrance");
        timeline.append(100, RhythmInput.LEFT, 0.7);
        timeline.append(800, RhythmInput.RIGHT, 0.7);
        timeline.markAudioComplete(800);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        session.beginAt(750);

        assertEquals(0, session.advance(750, chart));
        assertEquals(RhythmJudgement.PERFECT,
                session.input(RhythmInput.RIGHT, 800, chart).judgement());
        assertEquals(0, session.view().misses());
    }

    @Test
    void cuePublishedTooLateCanBeIgnoredWithoutPenalty() {
        RhythmTimeline timeline = new RhythmTimeline("late");
        RhythmCue cue = timeline.append(1_000, RhythmInput.FORWARD, 0.8);
        timeline.markAudioComplete(1_000);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0, RhythmDifficulty.NORMAL);

        session.ignore(cue.id());

        assertEquals(0, session.advance(2_000, chart));
        assertEquals(RhythmJudgement.NONE,
                session.input(RhythmInput.FORWARD, 1_000, chart).judgement());
        assertEquals(0, session.view().misses());
    }
}
