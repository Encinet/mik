package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;
import org.encinet.mik.protocol.music.MusicMediaDescriptor;
import org.encinet.mik.protocol.music.MusicRepresentation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaybackResourceResolverTest {
    private final MusicMedia media = mock(MusicMedia.class);
    private final PlaybackResourceResolver resolver = new PlaybackResourceResolver(media);

    @Test void exactReopenIncludesBothTrackAndAnalyzedVersion() throws Exception {
        var target = MusicTestTracks.target("song");
        var track = MusicTrack.reference(target);
        var audio = AudioTrackLoaderStreamingTest.lease(new byte[]{42}, "audio/wav", "old-version");
        doReturn(CompletableFuture.completedFuture(audio)).when(media).openExact(target, "old-version", MusicMedia.Readiness.COMPLETE);
        try (var playback = resolver.acquireExact(track, new MediaVersion("old-version")).join();
             var input = resolver.sourceManager().open(playback.identifier())) {
            assertEquals("old-version", playback.version().id());
            assertEquals(42, input.read());
        }
        verify(media).openExact(target, "old-version", MusicMedia.Readiness.COMPLETE);
        verifyNoMoreInteractions(media);
    }

    @Test void rejectsAndClosesNonAudioLeasesBeforeTheDecoder() {
        var lease = mock(MusicMedia.MediaLease.class);
        when(lease.descriptor()).thenReturn(MusicMediaDescriptor.newBuilder()
                .setRepresentation(MusicRepresentation.NOTE_EVENTS).setRepresentationVersion(1).build());
        assertThrows(IllegalArgumentException.class, () -> resolver.register(lease));
        verify(lease).close();
    }
}
