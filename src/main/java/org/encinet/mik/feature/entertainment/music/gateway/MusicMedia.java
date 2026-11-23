package org.encinet.mik.feature.entertainment.music.gateway;

import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.protocol.music.MusicFailure;
import org.encinet.mik.protocol.music.MusicMediaDescriptor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface MusicMedia {
    enum Readiness { READABLE, COMPLETE }

    CompletableFuture<? extends MediaLease> open(TrackTarget.Managed track, Readiness readiness);
    CompletableFuture<? extends MediaLease> openExact(TrackTarget.Managed track, String version, Readiness readiness);
    CompletableFuture<? extends MediaLease> openAvoiding(TrackTarget.Managed track, String version);
    CompletableFuture<Boolean> reportCorrupt(TrackTarget.Managed track, String version);

    interface MediaLease extends AutoCloseable {
        String mediaVersion();
        MusicMediaDescriptor descriptor();
        default long contentLength() { return descriptor().hasByteLength() ? descriptor().getByteLength() : -1L; }
        default String contentType() { return descriptor().hasContentType() ? descriptor().getContentType() : null; }
        Reader openReader() throws IOException;
        @Override void close();
    }

    interface Reader extends AutoCloseable {
        int read() throws IOException;
        int read(byte[] bytes, int offset, int length) throws IOException;
        long position();
        void seek(long position) throws IOException;
        int available() throws IOException;
        @Override void close() throws IOException;
    }

    final class ReadException extends IOException {
        private static final long serialVersionUID = 1L;
        private final MusicFailure.Code code;

        ReadException(String message, Throwable cause) {
            super(message, cause);
            code = MusicFailure.Code.MEDIA_IO;
        }

        public ReadException(MusicFailure.Code code) {
            super(code.name());
            this.code = code;
        }

        public MusicFailure.Code code() { return code; }
    }
}
