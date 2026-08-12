package org.encinet.mik.module.music.jukebox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.online.OnlineAudioCache;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.provider.AudioFrameProvider;
import su.plo.voice.api.server.audio.provider.AudioFrameResult;
import su.plo.voice.api.server.audio.source.AudioSender;
import su.plo.voice.api.server.audio.source.ServerProximitySource;
import su.plo.voice.api.server.player.VoicePlayer;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Streams local or cached online audio through a Plasmo Voice proximity source. */
final class AudioPlaybackEngine implements AutoCloseable {

    private final JavaPlugin plugin;
    private final PlasmoVoiceServer voiceServer;
    private final AudioTrackLoader loader;
    private final OfflineRhythmAnalyzer rhythmAnalyzer;
    private final ServerSourceLine sourceLine;
    private final Predicate<UUID> audibleToPlayer;
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    AudioPlaybackEngine(JavaPlugin plugin, PlasmoVoiceServer voiceServer,
                        OnlineAudioCache onlineCache,
                        Predicate<UUID> audibleToPlayer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.voiceServer = Objects.requireNonNull(voiceServer, "voiceServer");
        this.loader = new AudioTrackLoader(new PlaybackResourceResolver(onlineCache));
        this.rhythmAnalyzer = new OfflineRhythmAnalyzer(loader,
                message -> plugin.getLogger().warning(message));
        this.audibleToPlayer = Objects.requireNonNull(
                audibleToPlayer, "audibleToPlayer");
        this.sourceLine = voiceServer.getSourceLineManager()
                .createBuilder(plugin, "music", "soundCategory.record",
                        "plasmovoice:textures/icons/speaker_disc.png", 10)
                .setDefaultVolume(1.0)
                .build();
    }

    PlaybackSession create(Location location, MusicTrack music, String sourceName,
                           JukeboxSoundSettings settings, PlaybackCallbacks callbacks,
                           RhythmTimeline rhythmTimeline,
                           JukeboxExperienceMode experienceMode,
                           JukeboxPlaybackGroup playbackGroup) {
        if (closed.get()) {
            throw new IllegalStateException("Audio playback backend is closed");
        }
        Session session = new Session(location.clone(), music, sourceName, settings,
                callbacks, rhythmTimeline, experienceMode, playbackGroup);
        sessions.add(session);
        return session;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List.copyOf(sessions).forEach(Session::stop);
        sessions.clear();
        rhythmAnalyzer.close();
        loader.close();
        voiceServer.getSourceLineManager().unregister(sourceLine);
    }

    private final class Session implements PlaybackSession {
        private final Location location;
        private final MusicTrack music;
        private final String sourceName;
        private final PlaybackCallbacks callbacks;
        private final RhythmTimeline rhythmTimeline;
        private final JukeboxExperienceMode experienceMode;
        private final JukeboxPlaybackGroup playbackGroup;
        private final AtomicBoolean preparationStarted = new AtomicBoolean();
        private final AtomicBoolean playbackRequested = new AtomicBoolean();
        private final AtomicBoolean outputStarted = new AtomicBoolean();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean attemptCompleting = new AtomicBoolean();
        private final AtomicBoolean playbackStarted = new AtomicBoolean();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<AudioTrackLoader.LoadedAudio> loadedAudio = new AtomicReference<>();
        private final AtomicLong attempt = new AtomicLong();
        private final CorruptAudioRecovery corruptAudioRecovery = new CorruptAudioRecovery();
        private volatile JukeboxSoundSettings settings;
        private volatile PlaybackStatus status = PlaybackStatus.LOADING;
        private volatile AudioPlayer audioPlayer;
        private volatile long finalPositionMillis;
        private volatile ServerProximitySource<?> source;
        private volatile AudioSender sender;
        private volatile OfflineRhythmAnalyzer.Analysis rhythmAnalysis;

        private Session(Location location, MusicTrack music, String sourceName,
                        JukeboxSoundSettings settings, PlaybackCallbacks callbacks,
                        RhythmTimeline rhythmTimeline,
                        JukeboxExperienceMode experienceMode,
                        JukeboxPlaybackGroup playbackGroup) {
            this.location = location;
            this.music = Objects.requireNonNull(music, "music");
            this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
            this.settings = Objects.requireNonNull(settings, "settings");
            this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
            this.rhythmTimeline = Objects.requireNonNull(rhythmTimeline, "rhythmTimeline");
            this.experienceMode = Objects.requireNonNull(
                    experienceMode, "experienceMode");
            this.playbackGroup = Objects.requireNonNull(
                    playbackGroup, "playbackGroup");
        }

        @Override
        public void prepare() {
            if (!preparationStarted.compareAndSet(false, true) || stopped.get()) {
                return;
            }
            if (closed.get()) {
                fail(attempt.get(), new IllegalStateException("Audio playback backend is closed"));
                return;
            }
            prepareAttempt(attempt.get());
        }

        private void prepareAttempt(long expectedAttempt) {
            if (!current(expectedAttempt)) return;
            if (rhythmTimeline.complete()) {
                beginAudioLoad(expectedAttempt);
                return;
            }
            if (experienceMode.waitsForRhythmAnalysis()) {
                rhythmAnalysis = rhythmAnalyzer.analyze(music, rhythmTimeline);
                rhythmAnalysis.completion().whenComplete((ignored, error) ->
                        completeRhythmPreparationOnMainThread(expectedAttempt, error));
                return;
            }
            rhythmAnalysis = rhythmAnalyzer.analyze(music, rhythmTimeline);
            beginAudioLoad(expectedAttempt);
        }

        @Override
        public void start() {
            if (stopped.get() || terminal.get()) return;
            playbackRequested.set(true);
            prepare();
            startPreparedAudio(attempt.get());
        }

        private void completeRhythmPreparationOnMainThread(long expectedAttempt,
                                                            Throwable error) {
            if (!current(expectedAttempt)) return;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!current(expectedAttempt)) {
                        return;
                    }
                    if (!callbacks.isValid()) {
                        cancel(expectedAttempt);
                    } else if (error != null) {
                        fail(expectedAttempt, unwrap(error));
                    } else {
                        beginAudioLoad(expectedAttempt);
                    }
                });
            } catch (IllegalStateException exception) {
                fail(expectedAttempt, error == null ? exception : unwrap(error));
            }
        }

        private void beginAudioLoad(long expectedAttempt) {
            if (!current(expectedAttempt)) return;
            loader.load(music).whenComplete((loaded, error) -> {
                if (!current(expectedAttempt)) {
                    if (loaded != null) {
                        loaded.close();
                    }
                    return;
                }
                try {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> completeLoad(expectedAttempt, loaded, error));
                } catch (IllegalStateException exception) {
                    if (loaded != null) {
                        loaded.close();
                    }
                    fail(expectedAttempt, error == null ? exception : error);
                }
            });
        }

        private void completeLoad(long expectedAttempt,
                                  AudioTrackLoader.LoadedAudio loaded, Throwable error) {
            if (!current(expectedAttempt)) {
                if (loaded != null) {
                    loaded.close();
                }
                return;
            }
            if (!callbacks.isValid()) {
                if (loaded != null) loaded.close();
                cancel(expectedAttempt);
                return;
            }
            if (error != null) {
                fail(expectedAttempt, error);
                return;
            }
            if (loaded == null) {
                fail(expectedAttempt, new IllegalStateException(
                        "Audio loader completed without a track"));
                return;
            }
            loadedAudio.set(loaded);
            startPreparedAudio(expectedAttempt);
        }

        private void startPreparedAudio(long expectedAttempt) {
            AudioTrackLoader.LoadedAudio loaded = loadedAudio.get();
            if (!playbackRequested.get() || loaded == null
                    || !current(expectedAttempt)
                    || !outputStarted.compareAndSet(false, true)) {
                return;
            }
            try {
                if (!callbacks.isValid()) {
                    cancel(expectedAttempt);
                    return;
                }
                AudioTrack track = loaded.track();
                long previousPosition = Math.max(finalPositionMillis,
                        playbackGroup.synchronizedPositionMillis());
                long resumePosition = CorruptAudioRecovery.resumePositionMillis(
                        previousPosition, track.isSeekable(), track.getDuration());
                finalPositionMillis = resumePosition;
                if (resumePosition > 0L) {
                    track.setPosition(resumePosition);
                } else if (previousPosition > 0L) {
                    plugin.getLogger().warning("Online audio for " + music.id()
                            + " is not seekable; recovery will restart from the beginning");
                }
                AudioPlayer player = loader.createPlayer();
                audioPlayer = player;
                player.setVolume(settings.volumePercent());
                player.addListener(new AudioEventAdapter() {
                    @Override
                    public void onTrackException(AudioPlayer ignored, AudioTrack failedTrack,
                                                 FriendlyException exception) {
                        markFailure(expectedAttempt, exception);
                    }

                    @Override
                    public void onTrackEnd(AudioPlayer ignored, AudioTrack endedTrack,
                                           AudioTrackEndReason reason) {
                        if (reason == AudioTrackEndReason.LOAD_FAILED) {
                            markFailure(expectedAttempt, new IllegalStateException(
                                    "Audio decoder failed to load the track"));
                        }
                    }
                });
                player.playTrack(track);

                ServerProximitySource<?> proximitySource = source;
                if (proximitySource == null) {
                    Block block = location.getBlock();
                    ServerPos3d position = new ServerPos3d(
                            voiceServer.getMinecraftServer().getWorld(block.getWorld()),
                            block.getX() + 0.5, block.getY() + 1.5, block.getZ() + 0.5);
                    proximitySource = sourceLine.createStaticSource(position, true);
                    source = proximitySource;
                    proximitySource.setName(sourceName);
                    proximitySource.<VoicePlayer>addFilter(voicePlayer ->
                            audibleToPlayer.test(voicePlayer.getInstance().getUuid()));
                }
                AudioFrameProvider provider = () -> provideFrame(
                        expectedAttempt, track, player);
                AudioSender audioSender = proximitySource.createAudioSender(
                        provider, () -> (short) settings.rangeBlocks());
                sender = audioSender;
                audioSender.onStop(() -> complete(expectedAttempt));
                status = PlaybackStatus.PLAYING;
                audioSender.start();
                if (current(expectedAttempt)) {
                    playbackStarted.set(true);
                    callbacks.started();
                }
            } catch (RuntimeException exception) {
                fail(expectedAttempt, exception);
            }
        }

        private AudioFrameResult provideFrame(long expectedAttempt,
                                              AudioTrack track, AudioPlayer player) {
            if (!current(expectedAttempt) || track.getState() == AudioTrackState.FINISHED
                    || track.getState() == AudioTrackState.INACTIVE && track.getPosition() > 0) {
                return AudioFrameResult.Finished.INSTANCE;
            }
            AudioFrame frame = player.provide();
            if (frame == null) {
                return new AudioFrameResult.Provided(null);
            }
            playbackGroup.publishPositionMillis(track.getPosition());
            try {
                return new AudioFrameResult.Provided(
                        voiceServer.getDefaultEncryption().encrypt(frame.getData()));
            } catch (Exception exception) {
                markFailure(expectedAttempt, exception);
                return AudioFrameResult.Finished.INSTANCE;
            }
        }

        private void markFailure(long expectedAttempt, Throwable error) {
            if (!current(expectedAttempt)) return;
            failure.compareAndSet(null, error);
            loader.invalidate(music);
        }

        private void fail(long expectedAttempt, Throwable error) {
            markFailure(expectedAttempt, error);
            complete(expectedAttempt);
        }

        private void complete(long expectedAttempt) {
            if (!current(expectedAttempt)
                    || !attemptCompleting.compareAndSet(false, true)) {
                return;
            }
            Throwable error = failure.get();
            if (error != null && corruptAudioRecovery.shouldRetry(music, error)) {
                retryCorruptAudio(expectedAttempt);
                return;
            }
            if (!terminal.compareAndSet(false, true) || stopped.get()) {
                attemptCompleting.set(false);
                return;
            }
            cleanup();
            if (error == null) {
                callbacks.finished();
            } else {
                callbacks.failed(error);
            }
        }

        private void retryCorruptAudio(long expectedAttempt) {
            if (attempt.get() != expectedAttempt) {
                attemptCompleting.set(false);
                return;
            }
            long nextAttempt = attempt.incrementAndGet();
            plugin.getLogger().warning("Discarded failed online audio stream for "
                    + music.id() + " and retrying the download once");
            cleanupAttempt(true);
            failure.set(null);
            outputStarted.set(false);
            status = playbackStarted.get() ? PlaybackStatus.PLAYING : PlaybackStatus.LOADING;
            try {
                callbacks.retrying();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Music playback retry callback failed: "
                        + exception.getMessage());
            } finally {
                attemptCompleting.set(false);
            }
            if (stopped.get() || closed.get() || terminal.get()) {
                sessions.remove(this);
                return;
            }
            prepareAttempt(nextAttempt);
            startPreparedAudio(nextAttempt);
        }

        private boolean current(long expectedAttempt) {
            return attempt.get() == expectedAttempt
                    && !stopped.get() && !closed.get() && !terminal.get();
        }

        private void cancel(long expectedAttempt) {
            if (attempt.get() != expectedAttempt
                    || !terminal.compareAndSet(false, true) || stopped.get()) {
                return;
            }
            cleanup();
            callbacks.cancelled();
        }

        @Override
        public PlaybackStatus status() {
            return stopped.get() || terminal.get() ? PlaybackStatus.STOPPED : status;
        }

        @Override
        public long positionMillis() {
            AudioTrackLoader.LoadedAudio loaded = loadedAudio.get();
            return loaded == null ? finalPositionMillis
                    : Math.max(finalPositionMillis, Math.max(0, loaded.track().getPosition()));
        }

        @Override
        public void updateSettings(JukeboxSoundSettings settings) {
            this.settings = Objects.requireNonNull(settings, "settings");
            AudioPlayer currentPlayer = audioPlayer;
            if (currentPlayer != null) {
                currentPlayer.setVolume(settings.volumePercent());
            }
        }

        @Override
        public void stop() {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            terminal.set(true);
            cleanup();
        }

        private void cleanup() {
            attempt.incrementAndGet();
            cleanupAttempt(false);
            sessions.remove(this);
        }

        private void cleanupAttempt(boolean preserveSource) {
            if (!preserveSource) {
                status = PlaybackStatus.STOPPED;
            }
            finalPositionMillis = Math.max(finalPositionMillis, positionMillis());
            AudioSender currentSender = sender;
            sender = null;
            if (currentSender != null) {
                currentSender.stop();
            }
            AudioPlayer currentPlayer = audioPlayer;
            audioPlayer = null;
            if (currentPlayer != null) {
                currentPlayer.destroy();
            }
            if (!preserveSource) {
                ServerProximitySource<?> currentSource = source;
                source = null;
                if (currentSource != null) {
                    currentSource.remove();
                }
            }
            AudioTrackLoader.LoadedAudio loaded = loadedAudio.getAndSet(null);
            if (loaded != null) {
                loaded.close();
            }
            OfflineRhythmAnalyzer.Analysis analysis = rhythmAnalysis;
            rhythmAnalysis = null;
            if (analysis != null) {
                analysis.close();
            }
        }

        private static Throwable unwrap(Throwable error) {
            Throwable current = error;
            while ((current instanceof java.util.concurrent.CompletionException
                    || current instanceof java.util.concurrent.ExecutionException)
                    && current.getCause() != null) {
                current = current.getCause();
            }
            return current;
        }
    }
}
