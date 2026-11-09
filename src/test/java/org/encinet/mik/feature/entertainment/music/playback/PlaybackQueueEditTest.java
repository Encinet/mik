package org.encinet.mik.feature.entertainment.music.playback;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackQueueEditTest {
    private QueueEntry entry(String name) { return QueueEntry.create(name, UUID.randomUUID()); }

    @Test void movementPreservesPlayedEntriesCurrentIdentityAndTheOtherPartition() {
        var past = entry("past"); var a = entry("a"); var b = entry("b");
        var current = entry("current"); var request = entry("request");
        var program = PlaybackProgram.empty().submit(List.of(past, a, b), PlaybackProgram.Submission.APPEND)
                .advance(true, new Random(1)).submit(List.of(current, request), PlaybackProgram.Submission.INSERT);
        var moved = program.movePending(Set.of(b.id()), EntryEdits.before(a.id()));
        assertEquals(List.of(past, b, a), moved.base());
        assertEquals(program.current(), moved.current());
        assertEquals(program.cursor(), moved.cursor());
        assertEquals(program.priority(), moved.priority());
        assertEquals(List.of(b, a), moved.pendingBase());
        assertThrows(IllegalStateException.class, () -> program.movePending(Set.of(b.id()), EntryEdits.before(past.id())));
        assertThrows(IllegalArgumentException.class, () -> program.movePending(Set.of(past.id()), EntryEdits.end()));
        assertThrows(IllegalArgumentException.class, () -> program.movePending(Set.of(current.id()), EntryEdits.end()));
        assertThrows(IllegalArgumentException.class, () -> program.movePending(Set.of(b.id(), request.id()), EntryEdits.end()));
        assertThrows(IllegalStateException.class, () -> program.movePending(Set.of(b.id()), EntryEdits.before(request.id())));
    }

    @Test void shuffleAllowsOrderedRequestsButNeverPretendsItsCandidatesHaveAPredictedOrder() {
        var a = entry("a"); var b = entry("b"); var current = entry("current"); var first = entry("first"); var second = entry("second");
        var program = PlaybackProgram.empty().submit(List.of(a, b), PlaybackProgram.Submission.APPEND)
                .withMode(PlaybackProgram.Mode.SHUFFLE).submit(List.of(current, first, second), PlaybackProgram.Submission.INSERT);
        assertTrue(program.movableEntries(Set.of(a.id())).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> program.movePending(Set.of(a.id()), EntryEdits.end()));
        var moved = program.movePending(Set.of(second.id()), EntryEdits.before(first.id()));
        assertEquals(List.of(second, first), moved.priority());
        assertEquals(program.base(), moved.base());
        assertEquals(program.current(), moved.current());
        assertEquals(List.of(b), moved.removePending(Set.of(a.id())).base());
    }

    @Test void repeatedSongsRemainDifferentOccurrencesAndUnchangedPositionsKeepTheirVersion() {
        var a = entry("same"); var b = entry("same"); var c = entry("other");
        var program = PlaybackProgram.empty().submit(List.of(a, b, c), PlaybackProgram.Submission.APPEND);
        var moved = program.movePending(Set.of(b.id()), EntryEdits.before(a.id()));
        assertEquals(List.of(b, a, c), moved.base());
        assertEquals(program.version() + 1, moved.version());
        assertSame(program, program.movePending(Set.of(a.id()), EntryEdits.before(b.id())));
        assertSame(program, program.movePending(Set.of(c.id()), EntryEdits.end()));
        assertEquals(List.of(a, c), program.removePending(Set.of(b.id())).base());
        var playing = program.advance(true, new Random(1));
        assertThrows(IllegalStateException.class, () -> playing.removePending(Set.of(a.id())));
        assertThrows(IllegalStateException.class, () -> program.removePending(Set.of(UUID.randomUUID())));
    }
}
