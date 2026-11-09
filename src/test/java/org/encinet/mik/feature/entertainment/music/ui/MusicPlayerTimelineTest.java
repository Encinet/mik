package org.encinet.mik.feature.entertainment.music.ui;

import java.time.Duration;
import java.util.*;
import org.encinet.mik.feature.entertainment.music.application.*;
import org.encinet.mik.feature.entertainment.music.catalog.*;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerTimelineTest {
    private final UUID target = UUID.randomUUID(), entry = UUID.randomUUID(), generation = UUID.randomUUID();
    private final Owner owner = new Owner();
    private final MusicPlayerTimeline clock = new MusicPlayerTimeline(owner);
    private MusicPlaybackSnapshot sample(UUID generation, PlaybackStatus status) {
        var original = MusicWorkspaceStateTest.track("song");
        var song = new MusicTrack(original.id(), new TrackDetails("Song", null, null, "mp3", new AudioProperties(null, null, Duration.ofSeconds(60))), original.target());
        return new MusicPlaybackSnapshot(target, entry, generation, song, status, 1000, System.nanoTime(), true, null);
    }

    @Test void rapidSeekAndWheelValuesCommitOnlyTheLastPositionAndExposeThePendingPreview() {
        var expected = sample(generation, PlaybackStatus.PLAYING); clock.accept(expected);
        clock.seek(expected, 10000); clock.seek(expected, 15000); clock.seek(expected, 20000);
        assertEquals(1, owner.scheduled.size());
        assertEquals(20000, clock.position());
        assertTrue(owner.applied.isEmpty());
        owner.scheduled.getFirst().run();
        assertEquals(List.of(20000L), owner.applied);
        assertEquals(1, owner.refreshed);
    }
    @Test void closingOrChangingPlaybackCancelsTheBurstAndEvenALateOldCallbackCannotFlushANewRequest() {
        var original = sample(generation, PlaybackStatus.PLAYING); clock.accept(original); clock.seek(original, 10000);
        Runnable old = owner.scheduled.getFirst();
        var next = sample(UUID.randomUUID(), PlaybackStatus.PLAYING); clock.accept(next); clock.seek(next, 50000);
        assertEquals(1, owner.cancelled);
        old.run(); assertTrue(owner.applied.isEmpty()); assertEquals(50000, clock.position());
        owner.scheduled.get(1).run(); assertEquals(List.of(50000L), owner.applied);
        clock.seek(next, 20000); clock.close(); owner.scheduled.getLast().run();
        assertEquals(2, owner.cancelled); assertEquals(List.of(50000L), owner.applied);
        assertEquals(0, clock.position());
    }
    @Test void targetChangesPauseAndStaleTrackInputCannotSeekTheNewPlayback() {
        var original = sample(generation, PlaybackStatus.PLAYING); clock.accept(original); clock.seek(original, 10000);
        owner.current = false; owner.scheduled.getFirst().run(); assertTrue(owner.applied.isEmpty());
        assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> clock.seek(original, 30000)).code());
        owner.current = true;
        clock.seek(original, 20000); clock.accept(sample(generation, PlaybackStatus.PAUSED));
        owner.scheduled.getLast().run(); assertTrue(owner.applied.isEmpty());
        clock.accept(sample(UUID.randomUUID(), PlaybackStatus.PLAYING));
        assertEquals(MusicProblem.Code.CONFLICT, assertThrows(MusicProblem.class, () -> clock.seek(original, 30000)).code());
    }
    @Test void repeatedSamplesOfTheSamePlaybackPreserveTheBurstAndEndClicksClampBeforeTheMediaEnd() {
        var expected = sample(generation, PlaybackStatus.PLAYING); clock.accept(expected); clock.seek(expected, 60000);
        clock.accept(sample(generation, PlaybackStatus.PLAYING));
        assertEquals(59999, clock.position());
        owner.scheduled.getFirst().run(); assertEquals(List.of(59999L), owner.applied);
    }
    @Test void physicalPlaybackCanSeekWithoutAQueueEntryButRestartingTheDiscInvalidatesPendingInput() {
        var digital = sample(generation, PlaybackStatus.PLAYING);
        var physical = new MusicPlaybackSnapshot(target, null, generation, digital.track(), digital.status(), 0, System.nanoTime(), true, null);
        clock.accept(physical); clock.seek(physical, 12000); owner.scheduled.getFirst().run();
        assertEquals(List.of(12000L), owner.applied);
        clock.seek(physical, 15000);
        clock.accept(new MusicPlaybackSnapshot(target, null, UUID.randomUUID(), physical.track(), physical.status(), 0, System.nanoTime(), true, null));
        owner.scheduled.getLast().run(); assertEquals(List.of(12000L), owner.applied);
    }

    private static final class Owner implements MusicPlayerTimeline.Owner {
        final List<Runnable> scheduled = new ArrayList<>();
        final List<Long> applied = new ArrayList<>();
        boolean current = true;
        int refreshed, cancelled;
        public boolean current(UUID target) { return current; }
        public Runnable later(Runnable action) { scheduled.add(action); return () -> cancelled++; }
        public void seek(MusicPlaybackSnapshot expected, long position) { applied.add(position); }
        public void refresh() { refreshed++; }
    }
}
