package org.encinet.mik.feature.entertainment.music.jukebox;

import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loads negotiated encoded audio into Lavaplayer and owns decoder resources. */
final class AudioTrackLoader implements AutoCloseable {

    private final PlaybackResourceResolver resources;
    private final AudioPlayerManager players;

    AudioTrackLoader(PlaybackResourceResolver resources) {
        this(resources, 48_000);
    }

    AudioTrackLoader(PlaybackResourceResolver resources, int sampleRate) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.players = new DefaultAudioPlayerManager();
        this.players.getConfiguration().setOutputFormat(new Pcm16AudioDataFormat(2, sampleRate, sampleRate / 50, false));
        this.players.setUseSeekGhosting(false);
        this.players.setFrameBufferDuration(200);
        this.players.registerSourceManager(resources.sourceManager());
    }

    CompletableFuture<LoadedAudio> loadAvoiding(MusicTrack music, MediaVersion version) {
        return loadPrepared(music, resources.acquireAvoiding(music, version));
    }

    PlaybackResourceResolver.Resource resource(MusicMedia.MediaLease lease) {
        return resources.register(lease);
    }

    CompletableFuture<LoadedAudio> loadExact(MusicTrack music, MediaVersion version) {
        return loadPrepared(music, resources.acquireExact(music, version));
    }

    private CompletableFuture<LoadedAudio> loadPrepared(MusicTrack music,
            CompletableFuture<PlaybackResourceResolver.Resource> acquiring) {
        CompletableFuture<LoadedAudio> result = new CompletableFuture<>();
        result.whenComplete((_, _) -> {
            if (result.isCancelled()) acquiring.cancel(false);
        });
        acquiring.whenComplete((resource, resolveError) -> {
            if (resolveError != null) {
                result.completeExceptionally(resolveError);
                return;
            }
            if (result.isDone()) {
                resource.close();
                return;
            }
            probe(music, resource, result);
        });
        return result;
    }

    CompletableFuture<LoadedAudio> load(MusicTrack music, PlaybackResourceResolver.Resource resource) {
        var result = new CompletableFuture<LoadedAudio>();
        probe(music, resource, result);
        return result;
    }

    private void probe(MusicTrack music, PlaybackResourceResolver.Resource resource,
                       CompletableFuture<LoadedAudio> result) {
        result.whenComplete((_, _) -> { if (result.isCancelled()) resource.close(); });
        if (result.isDone()) return;
        try {
            var task = players.loadItem(new AudioReference(resource.identifier(),
                    music.details().title(), null), new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        var loaded = new LoadedAudio(track, resource);
                        if (!result.complete(loaded)) loaded.close();
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        AudioTrack selected = playlist.getSelectedTrack();
                        if (selected == null && !playlist.getTracks().isEmpty()) {
                            selected = playlist.getTracks().getFirst();
                        }
                        if (selected == null) {
                            fail(result, resource,
                                    new IllegalStateException("The source contains no audio track"));
                        } else {
                            trackLoaded(selected);
                        }
                    }

                    @Override
                    public void noMatches() {
                        fail(result, resource,
                                new IllegalStateException("No audio track was found"));
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        fail(result, resource, exception);
                    }
            });
            result.whenComplete((_, _) -> { if (result.isCancelled()) task.cancel(true); });
        } catch (RuntimeException exception) {
            fail(result, resource, exception);
        }
    }

    AudioPlayer createPlayer() {
        return players.createPlayer();
    }

    CompletableFuture<Boolean> reportCorrupt(MusicTrack track, MediaVersion version) {
        return resources.reportCorrupt(track, version);
    }

    private static void fail(CompletableFuture<LoadedAudio> result,
                             PlaybackResourceResolver.Resource resource, Throwable error) {
        resource.close();
        result.completeExceptionally(new DecodeFailure(resource.version(), error));
    }

    @Override
    public void close() {
        players.shutdown();
    }

    static final class LoadedAudio implements AutoCloseable {
        private final AudioTrack track;
        private final PlaybackResourceResolver.Resource resource;
        private final AtomicBoolean closed = new AtomicBoolean();

        private LoadedAudio(AudioTrack track, PlaybackResourceResolver.Resource resource) {
            this.track = Objects.requireNonNull(track, "track");
            this.resource = Objects.requireNonNull(resource, "resource");
        }

        AudioTrack track() {
            return track;
        }

        MediaVersion version() { return resource.version(); }
        boolean seekable() { return resource.seekable(); }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                resource.close();
                track.stop();
            }
        }
    }

    static final class DecodeFailure extends IOException {
        private static final long serialVersionUID = 1L;
        private final transient MediaVersion version;

        private DecodeFailure(MediaVersion version, Throwable error) {
            super(error.getMessage(), error);
            this.version = version;
        }

        MediaVersion version() { return version; }
    }
}
