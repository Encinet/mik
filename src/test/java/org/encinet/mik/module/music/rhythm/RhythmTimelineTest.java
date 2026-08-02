package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmTimelineTest {

    @Test
    void publishesStableOccurrencesForNbsLoops() {
        RhythmTimeline timeline = new RhythmTimeline("loop");
        timeline.publishNbs(List.of(
                new RhythmTimeline.TimedInput(100, RhythmInput.FORWARD, 0.5),
                new RhythmTimeline.TimedInput(600, RhythmInput.JUMP, 0.8)
        ), 1_000, 500, 2);

        List<RhythmCue> cues = timeline.between(0, 2_000);

        assertEquals(List.of(100L, 600L, 1_100L, 1_600L),
                cues.stream().map(RhythmCue::timeMillis).toList());
        assertEquals(List.of(RhythmInput.FORWARD, RhythmInput.JUMP,
                        RhythmInput.JUMP, RhythmInput.JUMP),
                cues.stream().map(RhythmCue::input).toList());
        assertEquals(cues.size(), cues.stream().map(RhythmCue::id).distinct().count());
        assertTrue(timeline.complete());
    }

    @Test
    void deDuplicatesConcurrentAnalyzerPublication() {
        RhythmTimeline timeline = new RhythmTimeline("audio");
        RhythmCue first = timeline.append(500, RhythmInput.LEFT, 0.5);
        RhythmCue duplicate = timeline.append(515, RhythmInput.LEFT, 0.9);

        assertEquals(first, duplicate);
        assertEquals(1, timeline.baseCueCount());
        assertNotEquals(first.id(), timeline.append(515, RhythmInput.RIGHT, 0.9).id());
    }
}
