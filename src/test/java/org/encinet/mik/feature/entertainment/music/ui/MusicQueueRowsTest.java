package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.storage.StoredTrack;
import org.encinet.mik.feature.entertainment.music.playback.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicQueueRowsTest {
    private QueueEntry entry(String name) { return QueueEntry.create(name, UUID.randomUUID()); }
    @Test void sectionsRetainOccurrenceIdentitiesAndExcludeCurrentAndAlreadyPlayedEntries() {
        var a = entry("a"); var b = entry("b"); var c = entry("c"); var request = entry("r");
        var program = PlaybackProgram.empty().submit(List.of(a, b, c), PlaybackProgram.Submission.APPEND)
                .advance(true, new Random(1)).advance(true, new Random(1))
                .submit(List.of(entry("now"), request), PlaybackProgram.Submission.INSERT);
        var rows = MusicQueueRows.of(program);
        assertEquals(List.of(request, c), rows.stream().map(MusicQueueRows.Row::entry).toList());
        assertEquals(MusicQueueRows.Section.REQUESTS, rows.getFirst().section());
        assertEquals(MusicQueueRows.Section.CONTINUATION, rows.getLast().section());
    }
    @Test void shuffleShowsCandidatesAndClearingPendingNeverShowsOldContinuation() {
        var a = entry("a"); var b = entry("b"); var c = entry("c");
        var program = PlaybackProgram.empty().submit(List.of(a, b, c), PlaybackProgram.Submission.APPEND)
                .advance(true, new Random(1)).withMode(PlaybackProgram.Mode.SHUFFLE);
        assertEquals(Set.of(b, c), new HashSet<>(MusicQueueRows.of(program).stream().map(MusicQueueRows.Row::entry).toList()));
        assertTrue(MusicQueueRows.of(program.clearPending()).isEmpty());
    }
    @Test void savingIncludesCurrentAndPendingSongsInQueueOrderWithoutPlayedHistoryOrDuplicates() {
        var history = entry("history"); var interrupted = entry("interrupted"); var later = entry("later");
        var repeated = entry("later"); var missing = entry("missing"); var current = entry("current"); var request = entry("request");
        var program = PlaybackProgram.empty().submit(List.of(history, interrupted, later, repeated, missing), PlaybackProgram.Submission.APPEND)
                .advance(true, new Random(1)).advance(true, new Random(1))
                .submit(List.of(current, request), PlaybackProgram.Submission.INSERT);
        var tracks = new HashMap<String, StoredTrack>();
        for (String id : List.of("history", "interrupted", "later", "current", "request"))
            tracks.put(id, StoredTrack.of(MusicWorkspaceStateTest.track(id)));
        var saved = MusicQueueRows.saveTracks(program, tracks);
        assertEquals(List.of("current", "request", "later"), saved.stream().map(track -> track.id()).toList());
        tracks.clear();
        assertEquals(3, saved.size(), "the save choice freezes the visible queue rather than retaining a mutable source");
    }
    @Test void savingAfterClearingPendingRetainsOnlyTheCurrentSong() {
        var a = entry("a"); var b = entry("b");
        var program = PlaybackProgram.empty().submit(List.of(a, b), PlaybackProgram.Submission.APPEND).advance(true, new Random(1));
        var tracks = Map.of("a", StoredTrack.of(MusicWorkspaceStateTest.track("a")), "b", StoredTrack.of(MusicWorkspaceStateTest.track("b")));
        assertEquals(List.of(MusicWorkspaceStateTest.track("a")), MusicQueueRows.saveTracks(program.clearPending(), tracks));
        assertTrue(MusicQueueRows.saveTracks(PlaybackProgram.empty(), tracks).isEmpty());
    }
}
