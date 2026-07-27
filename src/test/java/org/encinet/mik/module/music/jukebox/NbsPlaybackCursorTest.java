package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsSong;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbsPlaybackCursorTest {

    @Test
    void emitsTicksAtSongTempoAndFinishes() {
        NbsPlaybackCursor cursor = new NbsPlaybackCursor(song(false, 0));

        assertEquals(List.of(0), ticks(cursor.poll(0)));
        assertEquals(List.of(), ticks(cursor.poll(50_000_000)));
        assertEquals(List.of(1), ticks(cursor.poll(100_000_000)));
        assertEquals(List.of(2), ticks(cursor.poll(200_000_000)));
        assertTrue(cursor.poll(300_000_000).finished());
    }

    @Test
    void honorsFiniteLoopCountAndLoopStart() {
        NbsPlaybackCursor cursor = new NbsPlaybackCursor(song(true, 1));

        assertEquals(List.of(0), ticks(cursor.poll(0)));
        assertEquals(List.of(1), ticks(cursor.poll(100_000_000)));
        assertEquals(List.of(2), ticks(cursor.poll(200_000_000)));
        assertEquals(List.of(1), ticks(cursor.poll(300_000_000)));
        assertEquals(List.of(2), ticks(cursor.poll(400_000_000)));
        assertTrue(cursor.poll(500_000_000).finished());
    }

    @Test
    void zeroLoopCountMeansInfiniteLooping() {
        NbsPlaybackCursor cursor = new NbsPlaybackCursor(song(true, 0));

        cursor.poll(0);
        cursor.poll(100_000_000);
        cursor.poll(200_000_000);
        assertFalse(cursor.poll(300_000_000).finished());
        assertFalse(cursor.poll(400_000_000).finished());
        assertFalse(cursor.poll(500_000_000).finished());
    }

    @Test
    void catchesUpAfterSchedulerDelayWithoutSlowingTheSong() {
        NbsSong delayedSong = new NbsSong(5, "Delayed", null, null, null,
                4, 5, false, 0, 0,
                java.util.stream.IntStream.range(0, 5)
                        .mapToObj(tick -> new NbsNote(tick, 0, 45, 100, 100, 0, 0))
                        .toList());
        NbsPlaybackCursor cursor = new NbsPlaybackCursor(delayedSong);

        assertEquals(List.of(0), ticks(cursor.poll(0)));
        NbsPlaybackCursor.PollResult delayed = cursor.poll(1_000_000_000);
        assertEquals(List.of(1, 2, 3, 4), ticks(delayed));
        assertFalse(delayed.finished());
        assertTrue(cursor.poll(1_250_000_000).finished());
    }

    @Test
    void dropsTimingDebtBeyondTheBoundedCatchUpWindow() {
        NbsSong longSong = new NbsSong(5, "Long", null, null, null,
                20, 200, false, 0, 0,
                java.util.stream.IntStream.range(0, 200)
                        .mapToObj(tick -> new NbsNote(tick, 0, 45, 100, 100, 0, 0))
                        .toList());
        NbsPlaybackCursor cursor = new NbsPlaybackCursor(longSong);

        assertEquals(List.of(0), ticks(cursor.poll(0)));
        NbsPlaybackCursor.PollResult delayed = cursor.poll(60_000_000_000L);

        assertEquals(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList(),
                ticks(delayed));
        assertFalse(delayed.finished());
        assertEquals(List.of(21), ticks(cursor.poll(60_050_000_000L)));
    }

    @Test
    void advancesMultipleSongTicksPerServerTickForHighTempoSongs() {
        NbsSong fastSong = new NbsSong(5, "Fast", null, null, null,
                40, 6, false, 0, 0,
                java.util.stream.IntStream.range(0, 6)
                        .mapToObj(tick -> new NbsNote(tick, 0, 45, 100, 100, 0, 0))
                        .toList());
        NbsPlaybackCursor cursor = new NbsPlaybackCursor(fastSong);

        assertEquals(List.of(0), ticks(cursor.poll(0)));
        assertEquals(List.of(1, 2), ticks(cursor.poll(50_000_000)));
        assertEquals(List.of(3, 4), ticks(cursor.poll(100_000_000)));
        assertTrue(cursor.poll(150_000_000).finished());
    }

    @Test
    void appliesTempoChangerEventsToFollowingTickIntervals() {
        NbsNote tempoChange = new NbsNote(1, 0, 45, 100, 100, 0, 300,
                0, 20, 0, 45,
                org.encinet.mik.module.music.catalog.nbs.NbsNoteType.TEMPO_CHANGE);
        NbsSong tempoChangingSong = new NbsSong(6, "Tempo", null, null, null,
                10, 3, false, 0, 0,
                List.of(new NbsNote(0, 0, 45, 100, 100, 0, 0), tempoChange,
                        new NbsNote(2, 0, 45, 100, 100, 0, 0)));
        NbsPlaybackCursor cursor = new NbsPlaybackCursor(tempoChangingSong);

        assertEquals(List.of(0), ticks(cursor.poll(0)));
        assertEquals(List.of(1, 2), ticks(cursor.poll(150_000_000)));
        assertTrue(cursor.poll(200_000_000).finished());
        assertEquals(java.time.Duration.ofSeconds(1), tempoChangingSong.duration());
    }

    @Test
    void restoresTheTempoAtTheLoopStart() {
        NbsNote fast = new NbsNote(0, 0, 45, 100, 100, 0, 300,
                0, 20, 0, 45,
                org.encinet.mik.module.music.catalog.nbs.NbsNoteType.TEMPO_CHANGE);
        NbsNote slow = new NbsNote(2, 0, 45, 100, 100, 0, 75,
                0, 20, 0, 45,
                org.encinet.mik.module.music.catalog.nbs.NbsNoteType.TEMPO_CHANGE);
        NbsSong loopingSong = new NbsSong(6, "Tempo loop", null, null, null,
                10, 3, true, 1, 1,
                List.of(fast,
                        new NbsNote(1, 0, 45, 100, 100, 0, 0), slow));
        NbsPlaybackCursor cursor = new NbsPlaybackCursor(loopingSong);

        assertEquals(List.of(0), ticks(cursor.poll(0)));
        assertEquals(List.of(1), ticks(cursor.poll(50_000_000)));
        assertEquals(List.of(2), ticks(cursor.poll(100_000_000)));
        assertEquals(List.of(1), ticks(cursor.poll(300_000_000)));
        assertEquals(List.of(2), ticks(cursor.poll(350_000_000)));
    }

    private static List<Integer> ticks(NbsPlaybackCursor.PollResult result) {
        return result.notes().stream().map(NbsNote::tick).toList();
    }

    private static NbsSong song(boolean loop, int maxLoops) {
        return new NbsSong(5, "Song", null, null, null,
                10, 3, loop, maxLoops, 1,
                List.of(
                        new NbsNote(0, 0, 45, 100, 100, 0, 0),
                        new NbsNote(1, 16, 45, 100, 100, 0, 0),
                        new NbsNote(2, 19, 45, 100, 100, 0, 0)));
    }
}
