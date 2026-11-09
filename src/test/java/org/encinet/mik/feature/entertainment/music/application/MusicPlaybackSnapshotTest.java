package org.encinet.mik.feature.entertainment.music.application;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.encinet.mik.feature.entertainment.music.catalog.*;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicPlaybackSnapshotTest {
    private final UUID target = UUID.randomUUID(), entry = UUID.randomUUID(), generation = UUID.randomUUID();
    private MusicPlaybackSnapshot sample(PlaybackStatus status, long position, Duration duration) {
        var track = new MusicTrack("song", new TrackDetails("Song", null, null, "mp3", new AudioProperties(null, null, duration)), new TrackTarget.LocalFile(Path.of("/tmp/song.mp3")));
        return new MusicPlaybackSnapshot(target, entry, generation, track, status, position, 1_000_000_000, true, null);
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
        assertFalse(first.samePlayback(new MusicPlaybackSnapshot(target, UUID.randomUUID(), generation, first.track(), first.status(), 0, 1, true, null)));
        assertFalse(first.samePlayback(new MusicPlaybackSnapshot(target, entry, UUID.randomUUID(), first.track(), first.status(), 0, 1, true, null)));
        assertFalse(first.samePlayback(new MusicPlaybackSnapshot(UUID.randomUUID(), entry, generation, first.track(), first.status(), 0, 1, true, null)));
        assertFalse(first.samePlayback(null));
    }
    @Test void unknownOrUnrepresentableDurationsRemainUnknownAndASeekableSampleMustHaveAnIdentity() {
        assertEquals(0, sample(PlaybackStatus.PLAYING, 0, null).durationMillis());
        assertEquals(0, sample(PlaybackStatus.PLAYING, 0, Duration.ofSeconds(Long.MAX_VALUE)).durationMillis());
        assertThrows(IllegalArgumentException.class, () -> new MusicPlaybackSnapshot(target, null, null, null, PlaybackStatus.STOPPED, 0, 1, true, null));
    }
    @Test void physicalDiscsUseTheBackendAttemptIdentityAndRejectAnotherRunOfTheSameDisc() {
        var digital = sample(PlaybackStatus.PLAYING, 0, Duration.ofSeconds(60));
        var physical = new MusicPlaybackSnapshot(target, null, generation, digital.track(), PlaybackStatus.PLAYING, 0, 1, true, null);
        assertTrue(physical.seekable());
        assertNull(physical.entry());
        assertFalse(physical.samePlayback(digital));
        assertFalse(physical.samePlayback(new MusicPlaybackSnapshot(target, null, UUID.randomUUID(), physical.track(), physical.status(), 0, 1, true, null)));
        assertTrue(physical.samePlayback(new MusicPlaybackSnapshot(target, null, generation, physical.track(), PlaybackStatus.PAUSED, 5000, 1, true, null)));
    }

    @Test void preparationCancellationRejectsACompletedCancelledOrReplacedAttemptEvenInsideTheSameRun() {
        UUID preparation = UUID.randomUUID();
        var track = sample(PlaybackStatus.PLAYING,0,Duration.ofSeconds(60)).track();
        var loading = new MusicPlaybackSnapshot(target,entry,generation,track,PlaybackStatus.LOADING,0,1,false,preparation);
        assertTrue(loading.samePreparation(new MusicPlaybackSnapshot(target,entry,generation,track,PlaybackStatus.LOADING,0,2,false,preparation)));
        assertFalse(loading.samePreparation(new MusicPlaybackSnapshot(target,entry,generation,track,PlaybackStatus.LOADING,0,2,false,UUID.randomUUID())));
        assertFalse(loading.samePreparation(new MusicPlaybackSnapshot(target,entry,generation,track,PlaybackStatus.LOADING,0,2,false,null)), "a durable edit is not cancellable");
        assertFalse(loading.samePreparation(sample(PlaybackStatus.PLAYING,0,Duration.ofSeconds(60))));
        assertFalse(loading.samePreparation(sample(PlaybackStatus.STOPPED,0,Duration.ofSeconds(60))));
        assertFalse(loading.samePreparation(null));
        assertThrows(IllegalArgumentException.class,()->new MusicPlaybackSnapshot(target,entry,generation,track,PlaybackStatus.PLAYING,0,1,false,preparation));
    }
}
