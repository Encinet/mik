package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.encinet.mik.feature.entertainment.music.device.JukeboxExperienceMode;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSoundSettings;
import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import org.encinet.mik.protocol.music.MusicFailure;
import org.encinet.mik.protocol.music.MusicMediaDescriptor;
import org.encinet.mik.protocol.music.MusicRepresentation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedPlaybackEngineTest {
    private static final String VERSION = "a".repeat(32);
    private final MusicMedia media = mock(MusicMedia.class);
    private final ManagedPlaybackEngine.AudioOutput audio = mock(ManagedPlaybackEngine.AudioOutput.class);
    private final ManagedPlaybackEngine.NoteOutput notes = mock(ManagedPlaybackEngine.NoteOutput.class);
    private final PlaybackCallbacks callbacks = mock(PlaybackCallbacks.class);
    private final ArrayDeque<Runnable> main = new ArrayDeque<>();
    private final JukeboxPlaybackGroup group = new JukeboxPlaybackGroup();
    private final RhythmTimeline timeline = new RhythmTimeline(MusicTestTracks.id("managed"));
    private final CompletableFuture<MusicMedia.MediaLease> opening = new CompletableFuture<>();

    @Test
    void outputsJoiningBeforeAndAfterNegotiationReceiveOnlyOneInitialLease() {
        doReturn(opening).when(media).open(MusicTestTracks.target("managed"), MusicMedia.Readiness.READABLE);
        var lease = lease(1);
        var owner = mock(PlaybackSession.class);
        var follower = mock(PlaybackSession.class);
        when(notes.create(any(), same(lease), any(), any(), same(timeline), same(group))).thenReturn(owner);
        when(notes.create(any(), isNull(), any(), any(), same(timeline), same(group))).thenReturn(follower);
        doAnswer(_ -> { lease.close(); return null; }).when(owner).stop();
        try (var engine = new ManagedPlaybackEngine(main::add, media, audio, notes)) {
            output(engine, 0).start();
            output(engine, 1).start();
            verify(media).open(MusicTestTracks.target("managed"), MusicMedia.Readiness.READABLE);
            verifyNoInteractions(notes, audio);
            assertTrue(opening.complete(lease));
            main.remove().run();
            output(engine, 2).start();
            verify(notes).create(eq(new Location(null, 0, 64, 0)), same(lease), any(), any(), same(timeline), same(group));
            verify(notes, times(2)).create(any(), isNull(), any(), any(), same(timeline), same(group));
            verify(owner).start();
            verify(follower, times(2)).start();
            verifyNoMoreInteractions(media);
            verifyNoInteractions(audio);
            verify(lease, never()).close();
        }
        verify(lease).close();
    }

    @Test
    void stoppingBeforeTheMainThreadAcceptsTheLeaseClosesItExactlyOnce() {
        doReturn(opening).when(media).open(any(), any());
        var lease = lease(1);
        try (var engine = new ManagedPlaybackEngine(main::add, media, audio, notes)) {
            var output = output(engine, 0);
            output.start();
            assertTrue(opening.complete(lease));
            output.stop();
            main.remove().run();
            verify(lease).close();
            verifyNoInteractions(notes, audio, callbacks);
        }
    }

    @Test
    void lastOutputCancelsTheNegotiationAndNeverStartsALateBackend() {
        doReturn(opening).when(media).open(any(), any());
        try (var engine = new ManagedPlaybackEngine(main::add, media, audio, notes)) {
            var output = output(engine, 0);
            output.start();
            output.stop();
            assertTrue(opening.isCancelled());
            main.remove().run();
            verifyNoInteractions(notes, audio, callbacks);
        }
    }

    @Test
    void restoredGroupUsesExactVersionAndRejectsUnsupportedDescriptors() {
        group.pinVersion(VERSION);
        group.seekTo(500);
        doReturn(opening).when(media).openExact(MusicTestTracks.target("managed"), VERSION, MusicMedia.Readiness.READABLE);
        var lease = lease(2);
        try (var engine = new ManagedPlaybackEngine(main::add, media, audio, notes)) {
            output(engine, 0).start();
            assertTrue(opening.complete(lease));
            main.remove().run();
            verify(lease).close();
            verify(callbacks).failed(argThat(error -> error instanceof MusicMedia.ReadException failure
                    && failure.code() == MusicFailure.Code.UNSUPPORTED_MEDIA));
            verifyNoInteractions(notes, audio);
            verify(media, never()).open(any(), any());
        }
    }

    private PlaybackSession output(ManagedPlaybackEngine engine, int x) {
        var track = MusicTestTracks.managed("managed", new TrackDetails("Managed", null, null, null));
        return engine.create(new Location(null, x, 64, 0), track, new JukeboxSoundSettings(100, 32),
                callbacks, timeline, JukeboxExperienceMode.MUSIC, group, () -> {});
    }

    private static MusicMedia.MediaLease lease(int representationVersion) {
        var lease = mock(MusicMedia.MediaLease.class);
        when(lease.mediaVersion()).thenReturn(VERSION);
        when(lease.descriptor()).thenReturn(MusicMediaDescriptor.newBuilder().setRepresentation(MusicRepresentation.NOTE_EVENTS)
                .setRepresentationVersion(representationVersion).setSeekable(true).build());
        return lease;
    }
}
