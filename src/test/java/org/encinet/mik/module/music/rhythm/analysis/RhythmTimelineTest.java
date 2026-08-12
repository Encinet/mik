package org.encinet.mik.module.music.rhythm.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmTimelineTest {

    @Test
    void publishesStableModeIndependentOccurrencesForNbsLoops() {
        RhythmTimeline timeline = new RhythmTimeline("loop");
        timeline.publish(RhythmSource.NBS_NOTES, List.of(
                new RhythmPulse(100, 0.5, -0.4, 0.2, 11L),
                new RhythmPulse(600, 0.8, 0.6, -0.3, 22L)
        ), 1_000, 500, 2);

        List<RhythmBeat> beats = timeline.between(0, 2_000);

        assertEquals(List.of(100L, 600L, 1_100L, 1_600L),
                beats.stream().map(RhythmBeat::timeMillis).toList());
        assertEquals(List.of(11L, 22L, 22L, 22L),
                beats.stream().map(RhythmBeat::signature).toList());
        assertEquals(beats.size(), beats.stream().map(RhythmBeat::id).distinct().count());
        assertEquals(RhythmSource.NBS_NOTES, timeline.source());
        assertTrue(timeline.complete());
    }

    @Test
    void deDuplicatesConcurrentExtractorPublicationWithoutKnowingAGameLane() {
        RhythmTimeline timeline = new RhythmTimeline("audio");
        RhythmBeat first = timeline.append(new RhythmPulse(500, 0.5));
        RhythmBeat duplicate = timeline.append(new RhythmPulse(515, 0.9));

        assertEquals(first, duplicate);
        assertEquals(1, timeline.baseBeatCount());
        assertNotEquals(first.id(), timeline.append(new RhythmPulse(536, 0.9)).id());
    }

    @Test
    void resetReturnsAFailedDecodeTimelineToLoadingState() {
        RhythmTimeline timeline = new RhythmTimeline("retry");
        timeline.publish(RhythmSource.NBS_NOTES,
                List.of(new RhythmPulse(250, 0.8)), 1_000, 100, 2);

        timeline.reset();

        assertFalse(timeline.complete());
        assertFalse(timeline.playable());
        assertEquals(0L, timeline.analyzedThroughMillis());
        assertEquals(RhythmSource.AUDIO_ANALYSIS, timeline.source());
        assertTrue(timeline.between(0, 5_000).isEmpty());
    }
}
