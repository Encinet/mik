package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmChartViewTest {

    @Test
    void difficultiesProjectDifferentDensityFromOneSharedAnalysis() {
        RhythmTimeline timeline = new RhythmTimeline("shared");
        for (long time = 0; time <= 800; time += 200) {
            timeline.append(time, RhythmInput.values()[(int) (time / 200)], 0.7);
        }
        timeline.markAudioComplete(800);
        RhythmChartView easy = new RhythmChartView(timeline, RhythmDifficulty.EASY);
        RhythmChartView expert = new RhythmChartView(timeline, RhythmDifficulty.EXPERT);

        assertEquals(2, easy.between(0, 800).size());
        assertEquals(5, expert.between(0, 800).size());
        assertEquals(5, timeline.baseCueCount());
    }

    @Test
    void strongestAccentWinsInsteadOfTheFirstTransient() {
        RhythmTimeline timeline = new RhythmTimeline("salience");
        timeline.append(100, RhythmInput.LEFT, 0.20);
        timeline.append(320, RhythmInput.RIGHT, 0.92);
        timeline.markAudioComplete(500);

        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);

        assertEquals(List.of(320L), chart.between(0, 500).stream()
                .map(RhythmCue::timeMillis).toList());
    }

    @Test
    void projectionNeverRepeatsAnImmediateLaneOrSpamsVerticalActions() {
        RhythmTimeline timeline = new RhythmTimeline("ergonomics");
        timeline.append(0, RhythmInput.JUMP, 0.9);
        timeline.append(200, RhythmInput.JUMP, 0.9);
        timeline.append(400, RhythmInput.JUMP, 0.9);
        timeline.markAudioComplete(600);

        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.EXPERT);
        List<RhythmCue> cues = chart.between(0, 600);

        assertEquals(3, cues.size());
        assertEquals(RhythmInput.JUMP, cues.getFirst().input());
        assertTrue(cues.get(1).input() != RhythmInput.JUMP
                && cues.get(1).input() != RhythmInput.SNEAK);
        for (int index = 1; index < cues.size(); index++) {
            assertTrue(cues.get(index - 1).input() != cues.get(index).input());
        }
    }

    @Test
    void streamingChartExposesOnlyFullyObservedSelectionWindows() {
        RhythmTimeline timeline = new RhythmTimeline("streaming-window");
        timeline.append(300, RhythmInput.FORWARD, 0.8);
        RhythmChartView chart = new RhythmChartView(timeline, RhythmDifficulty.NORMAL);

        timeline.advanceAnalyzedThrough(350);
        assertTrue(!chart.preparedThrough(300));
        assertEquals(List.of(), chart.between(0, 500));

        timeline.advanceAnalyzedThrough(500);
        assertTrue(!chart.preparedThrough(300));
        assertEquals(List.of(300L), chart.between(0, 500).stream()
                .map(RhythmCue::timeMillis).toList());

        timeline.advanceAnalyzedThrough(700);
        assertTrue(chart.preparedThrough(300));
        assertEquals(List.of(300L), chart.between(0, 500).stream()
                .map(RhythmCue::timeMillis).toList());
    }
}
