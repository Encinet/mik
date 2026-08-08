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
                           JukeboxExperienceMode experienceMode) {
        if (closed.get()) {
            throw new IllegalStateException("Audio playback backend is closed");
        }
        Session session = new Session(location.clone(), music, sourceName, settings,
                callbacks, rhythmTimeline, experienceMode);
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
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<AudioTrackLoader.LoadedAudio> loadedAudio = new AtomicReference<>();
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
                        JukeboxExperienceMode experienceMode) {
            this.location = location;
            this.music = Objects.requireNonNull(music, "music");
            this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
            this.settings = Objects.requireNonNull(settings, "settings");
            this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
            this.rhythmTimeline = Objects.requireNonNull(rhythmTimeline, "rhythmTimeline");
            this.experienceMode = Objects.requireNonNull(
                    experienceMode, "experienceMode");
        }

        @Override
        public void start() {
            if (!started.compareAndSet(false, true) || stopped.get()) {
                return;
            }
            if (closed.get()) {
                fail(new IllegalStateException("Audio playback backend is closed"));
                return;
            }
            if (experienceMode.waitsForRhythmAnalysis()) {
                if (rhythmTimeline.complete()) {
                    beginAudioLoad();
                    return;
                }
                rhythmAnalysis = rhythmAnalyzer.analyze(music, rhythmTimeline);
                rhythmAnalysis.completion().whenComplete((ignored, error) ->
                        completeRhythmPreparationOnMainThread(error));
                return;
            }
            rhythmAnalysis = rhythmAnalyzer.analyze(music, rhythmTimeline);
            beginAudioLoad();
        }

        private void completeRhythmPreparationOnMainThread(Throwable error) {
            if (stopped.get() || closed.get()) return;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (stopped.get() || closed.get() || !callbacks.isValid()) {
                        cancel();
                    } else if (error != null) {
                        fail(unwrap(error));
                    } else {
                        beginAudioLoad();
                    }
                });
            } catch (IllegalStateException exception) {
                fail(error == null ? exception : unwrap(error));
            }
        }

        private void beginAudioLoad() {
            if (stopped.get() || closed.get()) return;
            loader.load(music).whenComplete((loaded, error) -> {
                if (stopped.get() || closed.get()) {
                    if (loaded != null) {
                        loaded.close();
                    }
                    return;
                }
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> completeLoad(loaded, error));
                } catch (IllegalStateException exception) {
                    if (loaded != null) {
                        loaded.close();
                    }
                    fail(error == null ? exception : error);
                }
            });
        }

        private void completeLoad(AudioTrackLoader.LoadedAudio loaded, Throwable error) {
            if (stopped.get() || closed.get() || !callbacks.isValid()) {
                if (loaded != null) {
                    loaded.close();
                }
                cancel();
                return;
            }
            if (error != null) {
                fail(error);
                return;
            }
            loadedAudio.set(loaded);
            try {
                AudioTrack track = loaded.track();
                AudioPlayer player = loader.createPlayer();
                audioPlayer = player;
                player.setVolume(settings.volumePercent());
                player.addListener(new AudioEventAdapter() {
                    @Override
                    public void onTrackException(AudioPlayer ignored, AudioTrack failedTrack,
                                                 FriendlyException exception) {
                        markFailure(exception);
                    }

                    @Override
                    public void onTrackEnd(AudioPlayer ignored, AudioTrack endedTrack,
                                           AudioTrackEndReason reason) {
                        if (reason == AudioTrackEndReason.LOAD_FAILED) {
                            markFailure(new IllegalStateException("Audio decoder failed to load the track"));
                        }
                    }
                });
                player.playTrack(track);

                Block block = location.getBlock();
                ServerPos3d position = new ServerPos3d(
                        voiceServer.getMinecraftServer().getWorld(block.getWorld()),
                        block.getX() + 0.5, block.getY() + 1.5, block.getZ() + 0.5);
                ServerProximitySource<?> proximitySource = sourceLine.createStaticSource(position, true);
                source = proximitySource;
                proximitySource.setName(sourceName);
                proximitySource.<VoicePlayer>addFilter(voicePlayer ->
                        audibleToPlayer.test(voicePlayer.getInstance().getUuid()));
                AudioFrameProvider provider = () -> provideFrame(track, player);
                AudioSender audioSender = proximitySource.createAudioSender(
                        provider, () -> (short) settings.rangeBlocks());
                sender = audioSender;
                audioSender.onStop(this::complete);
                status = PlaybackStatus.PLAYING;
                audioSender.start();
                if (!terminal.get() && !stopped.get()) {
                    callbacks.started();
                }
            } catch (RuntimeException exception) {
                fail(exception);
            }
        }

        private AudioFrameResult provideFrame(AudioTrack track, AudioPlayer player) {
            if (stopped.get() || track.getState() == AudioTrackState.FINISHED
                    || track.getState() == AudioTrackState.INACTIVE && track.getPosition() > 0) {
                return AudioFrameResult.Finished.INSTANCE;
            }
            AudioFrame frame = player.provide();
            if (frame == null) {
                return new AudioFrameResult.Provided(null);
            }
            try {
                return new AudioFrameResult.Provided(
                        voiceServer.getDefaultEncryption().encrypt(frame.getData()));
            } catch (Exception exception) {
                markFailure(exception);
                return AudioFrameResult.Finished.INSTANCE;
            }
        }

        private void markFailure(Throwable error) {
            failure.compareAndSet(null, error);
            loader.invalidate(music);
        }

        private void fail(Throwable error) {
            markFailure(error);
            complete();
        }

        private void complete() {
            if (!terminal.compareAndSet(false, true) || stopped.get()) {
                return;
            }
            Throwable error = failure.get();
            cleanup();
            if (error == null) {
                callbacks.finished();
            } else {
                callbacks.failed(error);
            }
        }

        private void cancel() {
            if (!terminal.compareAndSet(false, true) || stopped.get()) {
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
            return loaded == null ? finalPositionMillis : Math.max(0, loaded.track().getPosition());
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
            status = PlaybackStatus.STOPPED;
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
            ServerProximitySource<?> currentSource = source;
            source = null;
            if (currentSource != null) {
                currentSource.remove();
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
            sessions.remove(this);
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
