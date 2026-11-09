package org.encinet.mik.feature.entertainment.music.application;

import org.encinet.mik.feature.entertainment.music.catalog.*;
import org.encinet.mik.feature.entertainment.music.playback.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MusicSubmissionTest {
    @TempDir Path directory;
    private MusicTrack track() { return new MusicTrack("a", new TrackDetails("a", null, null, "mp3", AudioProperties.EMPTY), new TrackTarget.LocalFile(Path.of("/tmp/a"))); }
    @Test void oneTargetAndItsSongsAreFrozenAndACompletedSubmissionCannotReplay() {
        UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
        var tracks = new ArrayList<>(List.of(track()));
        var request = new MusicSubmission(actor, target, tracks, PlaybackProgram.Submission.INSERT);
        tracks.clear();
        assertEquals(target, request.target());
        assertEquals(1, request.tracks().size());
        request.begin(actor); request.finish();
        assertFalse(request.retryable());
        assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> request.begin(actor)).code());
    }
    @Test void requestsCannotBeReusedByAnotherPlayerOrRunTwiceConcurrently() {
        var actor = UUID.randomUUID(); var request = new MusicSubmission(actor, UUID.randomUUID(), List.of(track()), PlaybackProgram.Submission.INSERT);
        assertEquals(MusicProblem.Code.FORBIDDEN, assertThrows(MusicProblem.class, () -> request.begin(UUID.randomUUID())).code());
        request.begin(actor);
        assertEquals(MusicProblem.Code.BUSY, assertThrows(MusicProblem.class, () -> request.begin(actor)).code());
    }
    @Test void retryingACommittedAppendCannotStartPlayback() {
        UUID actor = UUID.randomUUID();
        var request = new MusicSubmission(actor, UUID.randomUUID(), List.of(track()), PlaybackProgram.Submission.APPEND);
        var program = PlaybackProgram.empty().submit(List.of(QueueEntry.create("a", actor)), PlaybackProgram.Submission.APPEND);
        request.begin(actor);
        request.attempt(() -> program, _ -> true, committed -> {
            committed.accept(program, 1L);
            return CompletableFuture.failedFuture(new MusicProblem(MusicProblem.Code.OFFLINE));
        }, () -> fail("first attempt cannot retry output"));
        request.failed(); request.begin(actor);
        request.attempt(() -> program, _ -> true, _ -> { fail("append inserted twice"); return null; },
                () -> fail("append retry started playback")).join();
        request.finish();
        assertFalse(request.retryable());
    }
    @Test void committedQueueSurvivesOutputFailureAndRetryNeverInsertsAgain() {
        try (var store = new MusicStore()) {
            store.open(directory.resolve("music.sqlite")).join();
            var actor = UUID.randomUUID(); var target = UUID.randomUUID();
            var request = new MusicSubmission(actor, target, List.of(track()), PlaybackProgram.Submission.INSERT);
            var writes = new AtomicInteger(); var outputs = new AtomicInteger();
            var intents = new PlaybackIntents(); long generation = intents.begin(target);
            request.begin(actor);
            var first = request.attempt( () -> store.snapshot().programs().get(target), intent -> intents.current(target, intent), committed ->
                    store.transact(editor -> {
                        writes.incrementAndGet();
                        var next = PlaybackProgram.empty().submit(List.of(QueueEntry.create("a", actor)), request.placement());
                        editor.programs.put(target, next);
                        return next;
                    }).thenAccept(program -> { committed.accept(program, generation); throw new MusicProblem(MusicProblem.Code.OFFLINE); }),
                    () -> fail("No receipt exists yet"));
            assertThrows(CompletionException.class, first::join);
            request.failed();
            assertTrue(request.retryable());
            request.begin(actor);
            request.attempt( () -> store.snapshot().programs().get(target), intent -> intents.current(target, intent), _ -> { fail("Inserted twice"); return null; }, outputs::incrementAndGet).join();
            assertEquals(1, writes.get()); assertEquals(1, outputs.get());
            intents.cancel(target);
            assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> request.attempt(
                    () -> store.snapshot().programs().get(target), intent -> intents.current(target, intent), _ -> { fail("Cancelled request inserted again"); return null; },
                    () -> fail("A newer stop was undone by a stale retry"))).code());
            assertEquals("a", store.snapshot().programs().get(target).current().trackId());
            store.transact(editor -> { editor.programs.put(target, editor.programs.get(target).clearPending()); return null; }).join();
            assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> request.attempt(
                    () -> store.snapshot().programs().get(target), _ -> true, _ -> { fail("Stale request inserted again"); return null; }, () -> fail("Stale queue resumed"))).code());
        }
    }
}
