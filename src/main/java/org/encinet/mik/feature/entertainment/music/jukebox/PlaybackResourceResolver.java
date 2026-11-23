package org.encinet.mik.feature.entertainment.music.jukebox;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Resolves playable audio resources without leaking cache policy into the decoder. */
final class PlaybackResourceResolver {

    private final MusicMedia media;
    private final PreparedAudioSourceManager sourceManager = new PreparedAudioSourceManager();
    PlaybackResourceResolver(MusicMedia media) {
        this.media = Objects.requireNonNull(media, "media");
    }

    PreparedAudioSourceManager sourceManager() { return sourceManager; }

    CompletableFuture<Resource> acquireExact(MusicTrack track, MediaVersion version) {
        return acceptLease(media.openExact((TrackTarget.Managed) track.target(), version.id(), MusicMedia.Readiness.COMPLETE));
    }

    CompletableFuture<Resource> acquireAvoiding(MusicTrack track, MediaVersion version) {
        return acceptLease(media.openAvoiding((TrackTarget.Managed) track.target(), version.id()));
    }

    Resource register(MusicMedia.MediaLease lease) {
        try {
            if (lease.descriptor().getRepresentation() != org.encinet.mik.protocol.music.MusicRepresentation.ENCODED_AUDIO
                    || lease.descriptor().getRepresentationVersion() != 1)
                throw new IllegalArgumentException("Not an encoded audio representation");
            String type = lease.contentType();
            var hints = MediaContainerHints.from(type, PreparedAudioSourceManager.extensionFor(type));
            String identifier = sourceManager.register(hints, () -> new LeasedAudioInput(lease));
            return new Resource(identifier, () -> {
                sourceManager.unregister(identifier);
                lease.close();
            }, new MediaVersion(lease.mediaVersion()), lease.descriptor().getSeekable());
        } catch (RuntimeException failure) {
            lease.close();
            throw failure;
        }
    }

    private CompletableFuture<Resource> acceptLease(CompletableFuture<? extends MusicMedia.MediaLease> opening) {
        var result = new CompletableFuture<Resource>();
        result.whenComplete((_, _) -> { if (result.isCancelled()) opening.cancel(false); });
        opening.whenComplete((lease, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            try {
                var resource = register(lease);
                if (!result.complete(resource)) resource.close();
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    CompletableFuture<Boolean> reportCorrupt(MusicTrack track, MediaVersion version) {
        return media.reportCorrupt((TrackTarget.Managed) track.target(), version.id());
    }

    static final class Resource implements AutoCloseable {
        private final String identifier;
        private final Runnable release;
        private final MediaVersion version;
        private final boolean seekable;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Resource(String identifier, Runnable release, MediaVersion version, boolean seekable) {
            this.identifier = Objects.requireNonNull(identifier, "identifier");
            this.release = Objects.requireNonNull(release, "release");
            this.version = version;
            this.seekable = seekable;
        }

        String identifier() {
            return identifier;
        }

        MediaVersion version() { return version; }
        boolean seekable() { return seekable; }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
