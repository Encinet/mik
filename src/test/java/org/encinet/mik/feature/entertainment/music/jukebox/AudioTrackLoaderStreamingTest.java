package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;
import org.encinet.mik.protocol.music.MusicMediaDescriptor;
import org.encinet.mik.protocol.music.MusicRepresentation;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AudioTrackLoaderStreamingTest {

    @Test
    void probesAudioThroughTheOpenedMediaLease() throws Exception {
        var track = MusicTestTracks.managed("streaming", new TrackDetails("Streaming", "Test", null, null));
        var resolver = new PlaybackResourceResolver(mock(MusicMedia.class));
        try (var loader = new AudioTrackLoader(resolver);
             var resource = resolver.register(wavLease(OfflineRhythmAnalyzerTest.wav(16_000)));
             var loaded = loader.load(track, resource).get(5, TimeUnit.SECONDS)) {
            assertEquals(1_000L, loaded.track().getDuration());
        }
    }

    static MusicMedia.MediaLease wavLease(byte[] bytes) {
        return lease(bytes, "audio/wav", "test-wav");
    }

    static MusicMedia.MediaLease lease(byte[] bytes, String type, String version) {
        return lease(bytes, version, MusicMediaDescriptor.newBuilder().setRepresentationVersion(1)
                .setRepresentation(MusicRepresentation.ENCODED_AUDIO).setContentType(type)
                .setByteLength(bytes.length).setSeekable(true).build());
    }

    static MusicMedia.MediaLease lease(byte[] bytes, String version, MusicMediaDescriptor descriptor) {
        return new MusicMedia.MediaLease() {
            @Override public String mediaVersion() { return version; }
            @Override public MusicMediaDescriptor descriptor() { return descriptor; }
            @Override public void close() { }

            @Override
            public MusicMedia.Reader openReader() {
                return new MusicMedia.Reader() {
                    private final ByteBuffer buffer = ByteBuffer.wrap(bytes);

                    @Override public int read() { return buffer.hasRemaining() ? Byte.toUnsignedInt(buffer.get()) : -1; }
                    @Override public long position() { return buffer.position(); }
                    @Override public void seek(long position) { buffer.position((int) Math.min(position, bytes.length)); }
                    @Override public int available() { return buffer.remaining(); }
                    @Override public void close() { }

                    @Override
                    public int read(byte[] destination, int offset, int length) {
                        int count = Math.min(length, buffer.remaining());
                        if (count == 0) return length == 0 ? 0 : -1;
                        buffer.get(destination, offset, count);
                        return count;
                    }
                };
            }
        };
    }
}
