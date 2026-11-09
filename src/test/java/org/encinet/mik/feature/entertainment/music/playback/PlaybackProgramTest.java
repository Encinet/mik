package org.encinet.mik.feature.entertainment.music.playback;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaybackProgramTest {
    private final UUID actor = UUID.randomUUID();
    private QueueEntry e(String name) {
        return QueueEntry.create(name, actor);
    }
    private PlaybackProgram next(PlaybackProgram p, boolean natural) {
        return p.advance(natural, new Random(17));
    }
    private PlaybackProgram playing(QueueEntry... entries) {
        return next(PlaybackProgram.empty().submit(List.of(entries), PlaybackProgram.Submission.APPEND), false);
    }
    @Test
    void insertPreservesPendingAndNeverResumesInterruptedOccurrence() {
        QueueEntry a = e("a"), b = e("b"), x = e("x"), y = e("y");
        PlaybackProgram p = playing(a, b).submit(List.of(x, y), PlaybackProgram.Submission.INSERT);
        assertEquals(x, p.current());
        assertEquals(List.of(a, b), p.base());
        p = next(p, true);
        assertEquals(y, p.current());
        p = next(p, true);
        assertEquals(b, p.current());
        assertNull(next(p, true).current());
    }
    @Test
    void newInsertionPrecedesPendingSongsAndPreservesOccurrences() {
        QueueEntry a = e("same"), b = e("same"), c = e("c"), x = e("x"), y = e("y");
        PlaybackProgram p = playing(a)
                                    .submit(List.of(x, b), PlaybackProgram.Submission.INSERT)
                                    .submit(List.of(y, c), PlaybackProgram.Submission.INSERT);
        assertNotEquals(a.id(), b.id());
        assertEquals(y, p.current());
        assertEquals(List.of(a), p.base());
        p = next(p, true);
        assertEquals(c, p.current());
        assertEquals(b, next(p, true).current());
    }
    @Test
    void appendRetainsTheCurrentSongAndPendingSongsAndPreservesBatchOrder() {
        QueueEntry a = e("a"), b = e("b"), x = e("x"), y = e("y"), c = e("c"), d = e("d");
        PlaybackProgram p = playing(a, b).submit(List.of(x, y), PlaybackProgram.Submission.INSERT)
                                    .submit(List.of(c, d), PlaybackProgram.Submission.APPEND);
        assertEquals(x, p.current());
        assertEquals(List.of(y), p.priority());
        assertEquals(List.of(a, b, c, d), p.base());
        for (QueueEntry expected : List.of(y, b, c, d)) {
            p = next(p, true);
            assertEquals(expected, p.current());
        }
    }
    @Test
    void appendToAnIdleProgramQueuesSongsWithoutStartingThem() {
        QueueEntry a = e("a"), b = e("b");
        PlaybackProgram p = PlaybackProgram.empty().submit(List.of(a, b), PlaybackProgram.Submission.APPEND);
        assertNull(p.current());
        assertEquals(List.of(a, b), p.base());
        assertEquals(a, next(p, false).current());
    }
    @Test
    void repeatOneHonorsRequestsThenReturnsToLoopTrack() {
        QueueEntry a = e("a"), b = e("b"), x = e("x");
        PlaybackProgram p = playing(a, b)
                                    .withMode(PlaybackProgram.Mode.REPEAT_ONE)
                                    .submit(List.of(x), PlaybackProgram.Submission.INSERT);
        assertEquals(x, p.current());
        p = next(p, true);
        assertEquals(a, p.current());
        assertEquals(b, next(p, false).current());
    }
    @Test
    void clearingPendingFinishesCurrentThenStops() {
        QueueEntry a = e("a");
        PlaybackProgram p = playing(a, e("b"))
                                    .withMode(PlaybackProgram.Mode.REPEAT_ALL)
                                    .clearPending();
        assertEquals(a, p.current());
        assertNull(next(p, true).current());
    }
    @Test
    void shuffleVisitsEveryOccurrenceBeforeStartingAnotherCycle() {
        List<QueueEntry> items = List.of(e("a"), e("b"), e("c"), e("d"));
        PlaybackProgram p = PlaybackProgram.empty()
                                    .submit(items, PlaybackProgram.Submission.APPEND)
                                    .withMode(PlaybackProgram.Mode.SHUFFLE);
        for (int cycle = 0; cycle < 3; cycle++) {
            Set<UUID> visited = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                p = next(p, true);
                assertTrue(visited.add(p.current().id()));
            }
            assertEquals(4, visited.size());
        }
    }
    @Test
    void previousUsesActualHistoryAndRestartsAfterThreeSeconds() {
        QueueEntry a = e("a"), x = e("x");
        PlaybackProgram p = playing(a)
                                    .submit(List.of(x), PlaybackProgram.Submission.INSERT);
        assertSame(p, p.previous(3001));
        assertEquals(a, p.previous(500).current());
    }
    @Test
    void appendingDoesNotMutatePreviouslyCapturedProgram() {
        PlaybackProgram before = playing(e("a"));
        PlaybackProgram after = before.submit(List.of(e("b")), PlaybackProgram.Submission.APPEND);
        assertEquals(1, before.base().size());
        assertEquals(2, after.base().size());
        assertThrows(UnsupportedOperationException.class, () -> after.base().clear());
    }
}
