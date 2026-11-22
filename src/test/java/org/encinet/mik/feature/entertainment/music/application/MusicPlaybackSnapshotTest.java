package org.encinet.mik.feature.entertainment.music.application;

import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import java.time.Duration;
import java.util.UUID;
import org.encinet.mik.feature.entertainment.music.catalog.*;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicPlaybackSnapshotTest {
    private final UUID target = UUID.randomUUID(), entry = UUID.randomUUID(), generation = UUID.randomUUID();
    private MusicPlaybackSnapshot sample(PlaybackStatus status, long position, Duration duration) {
        var track = MusicTestTracks.managed("song", new TrackDetails("Song", null, null, Duration.ofSeconds(90)));
        var clock = new JukeboxPlaybackSnapshot(status, position, duration == null ? 0 : duration.toMillis(), true);
        return new MusicPlaybackSnapshot(target, entry, generation, track, clock, 1_000_000_000, null, null);
    }
    @Test void clockAdvancesLinearlyOnlyWhilePlayingAndStopsAtTheMediaEndOrStaleSampleHorizon() {
        var sample = sample(PlaybackStatus.PLAYING, 10000, Duration.ofSeconds(60));
        assertEquals(10500, sample.positionAt(1_500_000_000));
        assertEquals(11500, sample.positionAt(20_000_000_000L));
        assertEquals(10000, sample.positionAt(0));
        assertEquals(60000, sample(PlaybackStatus.PLAYING, 59990, Duration.ofSeconds(60)).positionAt(2_000_000_000));
        for (var status : PlaybackStatus.values()) if (status != PlaybackStatus.PLAYING)
            assertEquals(10000, sample(status, 10000, Duration.ofSeconds(60)).positionAt(2_000_000_000));
    }
    @Test void identityDistinguishesOccurrencesAndRestartsOfTheSameSongButIgnoresClockAndPauseChanges() {
        var first = sample(PlaybackStatus.PLAYING, 10000, Duration.ofSeconds(60));
        assertTrue(first.samePlayback(sample(PlaybackStatus.PAUSED, 25000, Duration.ofSeconds(60))));
        assertFalse(first.samePlayback(new MusicPlaybackSnapshot(target, UUID.randomUUID(), generation, first.track(), first.clock(), 1, null, null)));
        assertFalse(first.samePlayback(new MusicPlaybackSnapshot(target, entry, UUID.randomUUID(), first.track(), first.clock(), 1, null, null)));
        assertFalse(first.samePlayback(new MusicPlaybackSnapshot(UUID.randomUUID(), entry, generation, first.track(), first.clock(), 1, null, null)));
        assertFalse(first.samePlayback(null));
    }
    @Test void liveDurationDoesNotFallBackToCatalogAndSeekingRequiresAnIdentity() {
        assertEquals(0, sample(PlaybackStatus.PLAYING, 0, null).durationMillis());
        var seekable = new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, 0, 1000, true);
        assertThrows(IllegalArgumentException.class, () -> new MusicPlaybackSnapshot(target, null, null, null, seekable, 1, null, null));
    }
    @Test void physicalDiscsUseTheBackendAttemptIdentityAndRejectAnotherRunOfTheSameDisc() {
        var digital = sample(PlaybackStatus.PLAYING, 0, Duration.ofSeconds(60));
        var physical = new MusicPlaybackSnapshot(target, null, generation, digital.track(), digital.clock(), 1, null, null);
        assertTrue(physical.seekable());
        assertNull(physical.entry());
        assertFalse(physical.samePlayback(digital));
        assertFalse(physical.samePlayback(new MusicPlaybackSnapshot(target, null, UUID.randomUUID(), physical.track(), physical.clock(), 1, null, null)));
        var paused = new JukeboxPlaybackSnapshot(PlaybackStatus.PAUSED, 5000, 60_000, true);
        assertTrue(physical.samePlayback(new MusicPlaybackSnapshot(target, null, generation, physical.track(), paused, 1, null, null)));
    }

    @Test void preparationCancellationRejectsACompletedCancelledOrReplacedAttemptEvenInsideTheSameRun() {
        UUID preparation = UUID.randomUUID();
        var track = sample(PlaybackStatus.PLAYING,0,Duration.ofSeconds(60)).track();
        var waiting = new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, 0);
        var loading = new MusicPlaybackSnapshot(target,entry,generation,track,waiting,1,preparation, null);
        assertTrue(loading.samePreparation(new MusicPlaybackSnapshot(target,entry,generation,track,waiting,2,preparation, null)));
        assertFalse(loading.samePreparation(new MusicPlaybackSnapshot(target,entry,generation,track,waiting,2,UUID.randomUUID(), null)));
        assertFalse(loading.samePreparation(new MusicPlaybackSnapshot(target,entry,generation,track,waiting,2,null, null)), "a durable edit is not cancellable");
        assertFalse(loading.samePreparation(sample(PlaybackStatus.PLAYING,0,Duration.ofSeconds(60))));
        assertFalse(loading.samePreparation(sample(PlaybackStatus.STOPPED,0,Duration.ofSeconds(60))));
        assertFalse(loading.samePreparation(null));
        var playing = new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, 0);
        assertThrows(IllegalArgumentException.class,()->new MusicPlaybackSnapshot(target,entry,generation,track,playing,1,preparation, null));
    }
}
