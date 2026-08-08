package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmChartViewTest {

    @Test
    void difficultiesProjectDifferentDensityFromOneSharedExtraction() {
        RhythmTimeline timeline = new RhythmTimeline("shared");
        for (long time = 0; time <= 800; time += 200) {
            timeline.append(new RhythmPulse(time, 0.7));
        }
        timeline.markComplete(800);
        RhythmChartView easy = new RhythmChartView(timeline, RhythmDifficulty.EASY);
        RhythmChartView expert = new RhythmChartView(timeline, RhythmDifficulty.EXPERT);

        assertEquals(2, easy.between(0, 800).size());
        assertEquals(5, expert.between(0, 800).size());
        assertEquals(5, timeline.baseBeatCount());
    }

    @Test
    void strongestAccentWinsInsteadOfTheFirstTransient() {
        RhythmTimeline timeline = new RhythmTimeline("salience");
        timeline.append(new RhythmPulse(100, 0.20));
        timeline.append(new RhythmPulse(320, 0.92));
        timeline.markComplete(500);

        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);

        assertEquals(List.of(320L), chart.between(0, 500).stream()
                .map(RhythmCue::timeMillis).toList());
    }

    @Test
    void normalDifficultyKeepsARealFourBeatPerSecondPulseTrain() {
        RhythmTimeline timeline = new RhythmTimeline("normal-fast-pulse");
        for (long time = 0L; time <= 2_000L; time += 250L) {
            timeline.append(new RhythmPulse(time,
                    time % 500L == 0L ? 0.72 : 0.46));
        }
        timeline.markComplete(2_000L);

        List<Long> times = new RhythmChartView(timeline, RhythmDifficulty.NORMAL)
                .between(0L, 2_000L).stream().map(RhythmCue::timeMillis).toList();

        assertEquals(List.of(0L, 250L, 500L, 750L, 1_000L, 1_250L,
                1_500L, 1_750L, 2_000L), times);
    }

    @Test
    void normalDifficultyDoesNotTurnWindowQuantizedFourBeatPulseIntoHalfTime() {
        RhythmTimeline timeline = new RhythmTimeline("normal-window-jitter");
        List<Long> decodedTimes = List.of(130L, 370L, 630L, 870L,
                1_130L, 1_370L, 1_630L, 1_870L, 2_130L, 2_370L,
                2_630L, 2_870L, 3_130L, 3_370L, 3_630L, 3_870L);
        decodedTimes.forEach(time -> timeline.append(new RhythmPulse(time, 0.80)));
        timeline.markComplete(4_000L);

        List<Long> selected = new RhythmChartView(timeline, RhythmDifficulty.NORMAL)
                .between(0L, 4_000L).stream().map(RhythmCue::timeMillis).toList();

        assertEquals(decodedTimes, selected);
    }

    @Test
    void currentProjectionUsesOnlyFourDirectionsAndAvoidsImmediateRepeats() {
        RhythmTimeline timeline = new RhythmTimeline("four-directions");
        for (long time = 0; time <= 800; time += 200) {
            timeline.append(new RhythmPulse(time, 0.9, 0.0, 0.0, time + 7L));
        }
        timeline.markComplete(800);

        List<RhythmCue> cues = new RhythmChartView(
                timeline, RhythmDifficulty.EXPERT).between(0, 800);

        assertEquals(4, RhythmInput.values().length);
        assertEquals(5, cues.size());
        for (int index = 1; index < cues.size(); index++) {
            assertTrue(cues.get(index - 1).input() != cues.get(index).input());
        }
    }

    @Test
    void streamingChartExposesOnlyFullyObservedSelectionWindows() {
        RhythmTimeline timeline = new RhythmTimeline("streaming-window");
        timeline.append(new RhythmPulse(300, 0.8));
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);

        timeline.advanceAnalyzedThrough(350);
        assertTrue(!chart.preparedThrough(300));
        assertEquals(List.of(), chart.between(0, 500));

        timeline.advanceAnalyzedThrough(500);
        assertTrue(!chart.preparedThrough(300));
        assertEquals(List.of(), chart.between(0, 500));

        timeline.advanceAnalyzedThrough(700);
        assertTrue(chart.preparedThrough(300));
        assertEquals(List.of(300L), chart.between(0, 500).stream()
                .map(RhythmCue::timeMillis).toList());
    }
}
