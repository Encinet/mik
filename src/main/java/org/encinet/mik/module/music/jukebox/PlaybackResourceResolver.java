package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.online.OnlineAudioCache;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Resolves playable audio resources without leaking cache policy into the decoder. */
final class PlaybackResourceResolver {

    private final OnlineAudioCache onlineCache;
    private final LocalMediaPreparer localMediaPreparer;
    private final GrowingCacheAudioSourceManager streamingSourceManager;

    PlaybackResourceResolver(OnlineAudioCache onlineCache) {
        this(onlineCache, new LocalMediaPreparer(), new GrowingCacheAudioSourceManager());
    }

    PlaybackResourceResolver(OnlineAudioCache onlineCache,
                             LocalMediaPreparer localMediaPreparer) {
        this(onlineCache, localMediaPreparer, new GrowingCacheAudioSourceManager());
    }

    PlaybackResourceResolver(OnlineAudioCache onlineCache,
                             LocalMediaPreparer localMediaPreparer,
                             GrowingCacheAudioSourceManager streamingSourceManager) {
        this.onlineCache = Objects.requireNonNull(onlineCache, "onlineCache");
        this.localMediaPreparer = Objects.requireNonNull(
                localMediaPreparer, "localMediaPreparer");
        this.streamingSourceManager = Objects.requireNonNull(
                streamingSourceManager, "streamingSourceManager");
    }

    GrowingCacheAudioSourceManager streamingSourceManager() {
        return streamingSourceManager;
    }

    CompletableFuture<Resource> acquire(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        if (track.target() instanceof TrackTarget.LocalFile local) {
            return acquireLocal(local);
        }
        if (track.target() instanceof TrackTarget.Lx online) {
            return onlineCache.acquireStreaming(online).thenApply(streaming -> {
                String identifier = streamingSourceManager.register(streaming);
                return new Resource(identifier, () -> {
                    streamingSourceManager.unregister(identifier);
                    streaming.close();
                });
            });
        }
        return CompletableFuture.failedFuture(new IOException(
                "NBS tracks use the note-block playback engine"));
    }

    /** Resolves only immutable, complete media suitable for whole-track analysis. */
    CompletableFuture<Resource> acquireComplete(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        if (track.target() instanceof TrackTarget.LocalFile local) {
            return acquireLocal(local);
        }
        if (track.target() instanceof TrackTarget.Lx online) {
            return onlineCache.acquire(online).thenApply(cached ->
                    new Resource(cached.identifier(), cached::close));
        }
        return CompletableFuture.failedFuture(new IOException(
                "NBS tracks use the note-block playback engine"));
    }

    private CompletableFuture<Resource> acquireLocal(TrackTarget.LocalFile local) {
        try {
            return CompletableFuture.completedFuture(
                    new Resource(localMediaPreparer.prepare(local).toString(), () -> {}));
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    void invalidate(MusicTrack track) {
        if (track != null && track.target() instanceof TrackTarget.Lx online) {
            onlineCache.invalidate(online);
        }
    }

    void index(MusicTrack track) {
        if (track != null && track.target() instanceof TrackTarget.Lx) {
            onlineCache.indexAsync(track);
        }
    }

    static final class Resource implements AutoCloseable {
        private final String identifier;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Resource(String identifier, Runnable release) {
            this.identifier = Objects.requireNonNull(identifier, "identifier");
            this.release = Objects.requireNonNull(release, "release");
        }

        String identifier() {
            return identifier;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
