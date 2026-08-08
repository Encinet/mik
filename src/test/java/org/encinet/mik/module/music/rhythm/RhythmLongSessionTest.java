package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.module.music.rhythm.analysis.RhythmSource;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmLongSessionTest {

    @Test
    void infiniteLoopKeepsOnlyTheRecentGameplayWindow() {
        RhythmTimeline timeline = new RhythmTimeline("infinite-soak");
        timeline.publish(RhythmSource.NBS_NOTES, List.of(
                        new RhythmPulse(0L, 0.9),
                        new RhythmPulse(200L, 0.8),
                        new RhythmPulse(400L, 0.9),
                        new RhythmPulse(600L, 0.8),
                        new RhythmPulse(800L, 0.9)),
                1_000L, 0L, 0);
        RhythmChartView chart = new RhythmChartView(
                timeline, RhythmDifficulty.EXPERT);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0L, RhythmDifficulty.EXPERT);
        RhythmRadialPath path = new RhythmRadialPath("infinite-soak", 90.0);

        int playedCues = 0;
        for (long now = 0L; now <= 7_200_000L; now += 200L) {
            List<RhythmCue> cues = chart.between(now, now);
            assertEquals(1, cues.size());
            RhythmCue cue = cues.getFirst();
            assertTrue(session.hit(cue, now, chart).judgement()
                    != RhythmJudgement.NONE);
            path.angleDegrees(cue);
            playedCues++;

            long cutoff = Math.max(0L, now - 5_000L);
            chart.discardBefore(cutoff);
            session.discardBefore(cutoff);
            path.discardBefore(cutoff);
        }

        assertTrue(playedCues > 30_000);
        assertTrue(chart.retainedCueCount() <= 27,
                () -> "retained chart cues=" + chart.retainedCueCount());
        assertTrue(session.retainedJudgementCount() <= 26,
                () -> "retained judgements=" + session.retainedJudgementCount());
        assertTrue(path.retainedAngleCount() <= 26,
                () -> "retained radial angles=" + path.retainedAngleCount());
        assertTrue(session.view().score() > 50_000_000L);
    }
}
