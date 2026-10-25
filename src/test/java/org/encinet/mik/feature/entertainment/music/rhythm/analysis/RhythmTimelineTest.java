package org.encinet.mik.feature.entertainment.music.rhythm.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class RhythmTimelineTest {

    @Test
    void loopWindowsMatchExplicitlyExpandedTrackIncludingDuplicateTimes() {
        var random = new Random(0x4d494b);
        for (int scenario = 0; scenario < 100; scenario++) {
            long duration = 100 + random.nextInt(1_000);
            long start = random.nextLong(duration);
            int repeats = random.nextInt(5);
            var pulses = new ArrayList<RhythmPulse>();
            for (int index = 0; index < 40; index++)
                pulses.add(new RhythmPulse(random.nextLong(duration + 100), random.nextDouble()));
            var timeline = new RhythmTimeline("random");
            timeline.publish(RhythmSource.NBS_NOTES, pulses, duration, start, repeats);
            List<RhythmBeat> base = timeline.between(0, duration - 1);
            // Also retain source notes at/after duration, which publish permits.
            var source = pulses.stream().sorted(Comparator.comparingLong(RhythmPulse::timeMillis)).toList();
            var expanded = new ArrayList<RhythmBeat>();
            for (int index = 0; index < source.size(); index++)
                expanded.add(new RhythmBeat(index + 1L, source.get(index)));
            int effectiveRepeats = repeats == 0 ? 12 : repeats;
            for (int repeat = 1; repeat <= effectiveRepeats; repeat++) {
                for (RhythmBeat beat : base) {
                    if (beat.timeMillis() < start) continue;
                    long id = -(((long) repeat << 32) | beat.id());
                    expanded.add(beat.occurrence(id, beat.timeMillis() + repeat * (duration - start)));
                }
            }
            expanded.sort(Comparator.comparingLong(RhythmBeat::timeMillis).thenComparingLong(RhythmBeat::id));
            for (int query = 0; query < 20; query++) {
                long from = random.nextLong(duration + 10 * (duration - start));
                long to = from + random.nextLong(duration - start);
                assertEquals(expanded.stream().filter(beat -> beat.timeMillis() >= from
                                && beat.timeMillis() <= to).toList(), timeline.between(from, to));
            }
        }
    }

    @Test
    void emptyLoopAndLastRepresentableCycleTerminate() {
        var empty = new RhythmTimeline("empty-loop");
        empty.publish(RhythmSource.NBS_NOTES, List.of(new RhythmPulse(0, 0.5)), 100, 50, 0);
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertEquals(1, empty.between(0, Long.MAX_VALUE).size()));

        var tiny = new RhythmTimeline("last-cycle");
        tiny.publish(RhythmSource.NBS_NOTES, List.of(new RhythmPulse(0, 0.5)), 1, 0, 0);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertEquals(Long.MAX_VALUE, tiny.between(Long.MAX_VALUE, Long.MAX_VALUE)
                        .getFirst().timeMillis()));

        var large = new RhythmTimeline("overflow");
        large.publish(RhythmSource.NBS_NOTES, List.of(
                new RhythmPulse(Long.MAX_VALUE - 2, 0.5),
                new RhythmPulse(Long.MAX_VALUE - 1, 0.5)),
                Long.MAX_VALUE, Long.MAX_VALUE - 2, 1);
        assertEquals(List.of(Long.MAX_VALUE - 1, Long.MAX_VALUE),
                large.between(Long.MAX_VALUE - 1, Long.MAX_VALUE).stream()
                        .map(RhythmBeat::timeMillis).toList());
    }

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
