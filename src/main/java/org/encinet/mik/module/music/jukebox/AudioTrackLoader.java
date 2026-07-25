package org.encinet.mik.module.music.jukebox;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.encinet.mik.module.music.catalog.MusicTrack;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loads local or cached online audio into Lavaplayer and owns decoder resources. */
final class AudioTrackLoader implements AutoCloseable {

    private final PlaybackResourceResolver resources;
    private final AudioPlayerManager players;

    AudioTrackLoader(PlaybackResourceResolver resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.players = new DefaultAudioPlayerManager();
        this.players.registerSourceManager(new LocalAudioSourceManager());
    }

    CompletableFuture<LoadedAudio> load(MusicTrack music) {
        Objects.requireNonNull(music, "music");
        CompletableFuture<LoadedAudio> result = new CompletableFuture<>();
        resources.acquire(music).whenComplete((resource, resolveError) -> {
            if (resolveError != null) {
                result.completeExceptionally(resolveError);
                return;
            }
            try {
                players.loadItem(new AudioReference(resource.identifier(),
                        music.details().title(), null), new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        complete(result, new LoadedAudio(track, resource));
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        AudioTrack selected = playlist.getSelectedTrack();
                        if (selected == null && !playlist.getTracks().isEmpty()) {
                            selected = playlist.getTracks().getFirst();
                        }
                        if (selected == null) {
                            fail(result, resource, music,
                                    new IllegalStateException("The source contains no audio track"));
                        } else {
                            complete(result, new LoadedAudio(selected, resource));
                        }
                    }

                    @Override
                    public void noMatches() {
                        fail(result, resource, music,
                                new IllegalStateException("No audio track was found"));
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        fail(result, resource, music, exception);
                    }
                });
            } catch (RuntimeException exception) {
                fail(result, resource, music, exception);
            }
        });
        return result;
    }

    AudioPlayer createPlayer() {
        return players.createPlayer();
    }

    void invalidate(MusicTrack music) {
        resources.invalidate(music);
    }

    private static void complete(CompletableFuture<LoadedAudio> result, LoadedAudio loaded) {
        if (!result.complete(loaded)) {
            loaded.close();
        }
    }

    private void fail(CompletableFuture<LoadedAudio> result,
                      PlaybackResourceResolver.Resource resource,
                      MusicTrack music, Throwable error) {
        resources.invalidate(music);
        resource.close();
        result.completeExceptionally(error);
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

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                track.stop();
                resource.close();
            }
        }
    }
}
