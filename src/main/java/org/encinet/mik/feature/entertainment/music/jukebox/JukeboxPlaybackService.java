package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscKeys;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscResolver;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricDisplayService;
import org.encinet.mik.feature.entertainment.music.online.OnlineAudioCache;
import org.encinet.mik.feature.entertainment.music.online.OnlineMusicRequestLimiter;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import org.encinet.mik.feature.entertainment.music.rhythm.playback.RhythmAudioChannel;
import org.encinet.mik.feature.entertainment.music.rhythm.playback.RhythmParticipants;
import org.encinet.mik.feature.entertainment.music.rhythm.playback.RhythmPlaybackGateway;
import org.encinet.mik.feature.entertainment.music.rhythm.playback.RhythmPlaybackIsolation;
import org.encinet.mik.feature.entertainment.music.rhythm.playback.RhythmPlaybackSnapshot;
import org.encinet.mik.feature.entertainment.music.rhythm.playback.RhythmPlaybackState;
import org.encinet.mik.shared.util.ShutdownSequence;
import su.plo.voice.api.server.PlasmoVoiceServer;

/** Coordinates jukebox discs, playback state, notifications, and backend lifecycle. */
public class JukeboxPlaybackService
        implements JukeboxPlaybackStatus, RhythmPlaybackGateway, RhythmPlaybackIsolation, AutoCloseable {
    private final JavaPlugin plugin;
    private final MusicDiscResolver discResolver;
    private final JukeboxSettingsStore settingsStore;
    private final JukeboxPlaybackNotifier notifier;
    private final VanillaRecordSilencer recordSilencer;
    private final JukeboxAudioAudience audioAudience = new JukeboxAudioAudience();
    private final AudioPlaybackEngine audioEngine;
    private final NbsPlaybackEngine nbsEngine;
    private final LyricDisplayService lyricDisplay;
    private final OnlineMusicRequestLimiter requestLimiter;
    private final Consumer<MusicTrack> playbackRecorder;
    private final Map<JukeboxKey, Playback> playbacks = new ConcurrentHashMap<>();
    private final Map<JukeboxKey, FinishedPlayback> finishedPlaybacks = new ConcurrentHashMap<>();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<JukeboxKey, JukeboxPlaybackGroup> managedGroups = new ConcurrentHashMap<>();
    private final Map<UUID, JukeboxPlaybackGroup> programClocks = new ConcurrentHashMap<>();
    private volatile PhysicalStarted physicalStarted = (_, _, _) -> {};
    @FunctionalInterface
    public interface PhysicalStarted {
        void started(Location location, MusicTrack track, UUID actor);
    }
    public void setPhysicalStarted(PhysicalStarted listener) {
        physicalStarted = listener;
    }
    /** Identity changes when the same physical disc starts another backend attempt. */
    public record PhysicalPlayback(UUID id, MusicTrack track) {
        public PhysicalPlayback { java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(track); }
    }
    public PhysicalPlayback physicalPlayback(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        return playback != null && !playback.digital && !playback.stopped.get()
                ? new PhysicalPlayback(playback.attempt.id(), playback.music) : null;
    }
    private volatile CompletionListener completionListener = (_, _, _) -> {};
    @FunctionalInterface
    public interface CompletionListener {
        void ended(Location location, MusicTrack track, boolean failed);
    }
    public void setCompletionListener(CompletionListener listener) {
        completionListener = listener;
    }
    public void setOutputAudience(java.util.function.BiPredicate<Location, UUID> filter) {
        audioEngine.setOutputAudience(filter);
        nbsEngine.setOutputAudience(filter);
    }
    public boolean playProgram(
            Player player, Jukebox jukebox, MusicTrack track, UUID clock, Runnable started) {
        JukeboxKey key = JukeboxKey.of(jukebox.getLocation());
        managedGroups.put(key, programClocks.computeIfAbsent(clock, _ -> new JukeboxPlaybackGroup()));
        try {
            return startTrack(player, jukebox, track, true, PlaybackOrigin.AUTOMATIC, started);
        } finally {
            managedGroups.remove(key);
            programClocks.values().removeIf(
                    group -> playbacks.values().stream().noneMatch(p -> p.attempt.group() == group));
        }
    }

    public JukeboxPlaybackService(JavaPlugin plugin, PlasmoVoiceServer voiceServer,
            MusicDiscResolver discResolver, OnlineAudioCache audioCache, JukeboxPlaybackNotifier notifier,
            VanillaRecordSilencer recordSilencer, JukeboxSettingsStore settingsStore,
            Consumer<MusicTrack> playbackRecorder, LyricDisplayService lyricDisplay,
            OnlineMusicRequestLimiter requestLimiter) {
        this.plugin = plugin;
        this.discResolver = discResolver;
        this.notifier = notifier;
        this.recordSilencer = recordSilencer;
        this.settingsStore = java.util.Objects.requireNonNull(settingsStore, "settingsStore");
        this.playbackRecorder = java.util.Objects.requireNonNull(playbackRecorder, "playbackRecorder");
        this.lyricDisplay = java.util.Objects.requireNonNull(lyricDisplay, "lyricDisplay");
        this.requestLimiter = java.util.Objects.requireNonNull(requestLimiter, "requestLimiter");

        this.audioEngine = new AudioPlaybackEngine(plugin, voiceServer, audioCache, audioAudience::canHear);
        this.nbsEngine = new NbsPlaybackEngine(plugin, audioAudience::canHear);
    }

    public void enable() {
        if (closed.get())
            throw new IllegalStateException("Jukebox playback service is closed");
        enabled.set(true);
    }

    /** Starts only the new signed physical record format. */
    public boolean playInsertedDisc(Player player, Jukebox jukebox) {
        return playInsertedDisc(player, jukebox, PlaybackOrigin.PLAYER);
    }
    public boolean playInsertedDisc(Player player, Jukebox jukebox, PlaybackOrigin origin) {
        MusicTrack music = discResolver.resolve(jukebox.getRecord());
        if (music == null) {
            if (player != null)
                notifier.unavailableDisc(player);
            return false;
        }
        return startTrack(player, jukebox, music, false, origin, () -> {});
    }

    private boolean startTrack(Player player, Jukebox jukebox, MusicTrack music, boolean replaceRecord,
            PlaybackOrigin origin, Runnable onStarted) {
        java.util.Objects.requireNonNull(origin, "origin");
        if (!enabled.get())
            return false;
        Location location = jukebox.getLocation().clone();
        JukeboxKey key = JukeboxKey.of(location);
        if (!replaceRecord && !music.id().equals(MusicDiscKeys.trackId(jukebox.getRecord()))) {
            return false;
        }
        JukeboxSoundSettings settings = settingsStore.read(jukebox);
        JukeboxExperienceMode experienceMode = settingsStore.readExperienceMode(jukebox);
        JukeboxPlaybackCoordination.Decision coordination =
                coordinatePlayback(key, location, music, settings, experienceMode);
        if (coordination.blocked()) {
            if (player != null) {
                notifier.overlappingPlayback(player);
            }
            return false;
        }
        if (origin == PlaybackOrigin.PLAYER && player != null && music.target() instanceof TrackTarget.Lx) {
            OnlineMusicRequestLimiter.Decision decision = requestLimiter.tryAcquire(
                    player.getUniqueId(), OnlineMusicRequestLimiter.Operation.PLAYBACK);
            if (!decision.allowed()) {
                notifier.onlineRequestRateLimited(player, decision.retryAfterSeconds());
                return false;
            }
        }
        stop(key);
        finishedPlaybacks.remove(key);
        Playback playback = new Playback(key, location, music, player == null ? null : player.getUniqueId(),
                settings, experienceMode,
                coordination.playbackGroup() == null ? new JukeboxPlaybackGroup()
                                                     : coordination.playbackGroup(),
                origin, onStarted);
        playback.digital = replaceRecord;
        playbacks.put(key, playback);
        prepareJukebox(jukebox, replaceRecord);

        if (playback.experienceMode == JukeboxExperienceMode.MUSIC) {
            recordSilencer.playbackStarted(location);
            startBackend(playback);
        }
        return !playback.failed.get();
    }

    /** Explicit digital playback replaces the physical record before its backend validates the slot. */
    static void prepareJukebox(Jukebox jukebox, boolean replaceRecord) {
        jukebox.stopPlaying();
        if (replaceRecord)
            jukebox.setRecord(null);
        jukebox.update(true, false);
    }

    private JukeboxPlaybackCoordination.Decision coordinatePlayback(JukeboxKey key, Location location,
            MusicTrack music, JukeboxSoundSettings settings, JukeboxExperienceMode experienceMode) {
        JukeboxPlaybackGroup managed = managedGroups.get(key);
        if (managed != null)
            return new JukeboxPlaybackCoordination.Decision(false, managed);
        if (experienceMode != JukeboxExperienceMode.MUSIC) {
            return new JukeboxPlaybackCoordination.Decision(false, null);
        }
        List<JukeboxPlaybackCoordination.ActiveField> activeFields =
                playbacks.values()
                        .stream()
                        .filter(active -> !active.key.equals(key))
                        .filter(active -> !active.stopped.get() && !active.finishing.get())
                        .filter(active -> active.experienceMode == JukeboxExperienceMode.MUSIC)
                        .map(active
                                -> new JukeboxPlaybackCoordination.ActiveField(active.location,
                                        active.music.id(), active.settings, active.attempt.group()))
                        .toList();
        return JukeboxPlaybackCoordination.decide(location, music.id(), settings, activeFields);
    }

    /** Acquires a participant and prepares, but does not yet play, the transport. */
    @Override
    public Optional<Participation> join(Block block, UUID playerId) {
        java.util.Objects.requireNonNull(block, "block");
        java.util.Objects.requireNonNull(playerId, "playerId");
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || playback.stopped.get() || playback.finishing.get()
                || playback.experienceMode != JukeboxExperienceMode.RHYTHM) {
            return Optional.empty();
        }

        RhythmParticipants.Participation participant =
                playback.rhythmParticipants.acquire(playerId, () -> resetRhythmPlaybackToWaiting(playback));
        if (participant.first() && playback.attempt.session() == null) {
            recordSilencer.playbackStarted(playback.location);
            prepareBackend(playback);
        }
        if (playback.failed.get() || playbacks.get(playback.key) != playback) {
            participant.close();
            return Optional.empty();
        }

        UUID playbackId = playback.attempt.id();
        return Optional.of(new Participation() {
            private final AtomicBoolean closed = new AtomicBoolean();

            @Override
            public UUID playbackId() {
                return playbackId;
            }

            @Override
            public void startPlayback() {
                if (!closed.get())
                    startRhythmPlayback(playback, playbackId);
            }

            @Override
            public void close() {
                if (closed.compareAndSet(false, true))
                    participant.close();
            }
        });
    }

    /** Prepare a room without a participant and without starting its transport. */
    public void prepareRhythm(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback != null && playback.experienceMode == JukeboxExperienceMode.RHYTHM
                && playback.attempt.session() == null)
            prepareBackend(playback);
    }

    private void startRhythmPlayback(Playback playback, UUID playbackId) {
        if (playbacks.get(playback.key) != playback || playback.stopped.get()
                || !playback.attempt.id().equals(playbackId) || playback.rhythmParticipants.isEmpty()) {
            return;
        }
        playback.attempt.start();
    }

    /** Stops and discards the song clock while retaining the selected track for the next round. */
    private void resetRhythmPlaybackToWaiting(Playback playback) {
        if (!playback.rhythmParticipants.isEmpty() || playbacks.get(playback.key) != playback
                || playback.stopped.get() || playback.finishing.get()) {
            return;
        }
        PlaybackAttempt previous = playback.attempt;
        playback.attempt = new PlaybackAttempt();
        recordPlaybackIfQualified(playback, previous, false);
        previous.close();
        recordSilencer.playbackStopped(playback.location);
    }

    private void startBackend(Playback playback) {
        prepareBackend(playback);
        playback.attempt.start();
    }

    private void prepareBackend(Playback playback) {
        PlaybackAttempt attempt = playback.attempt;
        try {
            attempt.prepare(() -> {
                PlaybackCallbacks callbacks = callbacks(playback, attempt);
                return playback.music.target() instanceof TrackTarget.NbsFile nbs
                        ? nbsEngine.create(playback.location, nbs, playback.settings, callbacks,
                                  playback.rhythmTimeline, attempt.group())
                        : audioEngine.create(playback.location, playback.music,
                                  playback.music.details().title(), playback.settings, callbacks,
                                  playback.rhythmTimeline, playback.experienceMode, attempt.group());
            });
        } catch (RuntimeException exception) {
            if (isCurrentAttempt(playback, attempt))
                backendFailed(playback, exception);
        }
    }

    private boolean isCurrentAttempt(Playback playback, PlaybackAttempt attempt) {
        return enabled.get() && playbacks.get(playback.key) == playback && playback.attempt == attempt
                && attempt.active() && !playback.stopped.get() && !playback.finishing.get();
    }

    private PlaybackCallbacks callbacks(Playback playback, PlaybackAttempt attempt) {
        return attempt.guard(new PlaybackCallbacks() {
            @Override
            public boolean isValid() {
                if (!isCurrentAttempt(playback, attempt)) {
                    return false;
                }
                if (!playback.location.isChunkLoaded())
                    return false;
                Block block = playback.location.getBlock();
                return block.getState() instanceof Jukebox jukebox && hasPlaybackDisc(jukebox, playback);
            }

            @Override
            public void started() {
                if (!isCurrentAttempt(playback, attempt))
                    return;
                boolean firstStart = attempt.claimStarted();
                if (firstStart)
                    notifyStarted(playback);
                if (!isCurrentAttempt(playback, attempt))
                    return;
                PlaybackSession session = attempt.session();
                if (firstStart && session != null) {
                    LyricDisplayService.PlaybackLyrics lyrics =
                            lyricDisplay.start(playback.location, playback.music, session::positionMillis,
                                    this::isValid, () -> playback.settings.rangeBlocks());
                    attempt.attachLyrics(lyrics::close);
                }
                if (firstStart) {
                    if (playback.origin == PlaybackOrigin.PLAYER) {
                        notifier.playbackInaudible(playback.requestingPlayer, playback.music);
                    }
                    if (attempt.group().claimStartedAnnouncement()) {
                        notifier.broadcastStarted(playback.location, playback.music.details().title(),
                                playback.music, playback.settings.rangeBlocks());
                    }
                }
            }

            @Override
            public void retrying() {}

            @Override
            public void failed(Throwable error) {
                if (isCurrentAttempt(playback, attempt))
                    backendFailed(playback, error);
            }

            @Override
            public void finished() {
                if (!isCurrentAttempt(playback, attempt))
                    return;
                recordPlaybackIfQualified(playback, attempt, true);
                finishPlayback(playback);
            }

            @Override
            public void cancelled() {
                if (isCurrentAttempt(playback, attempt))
                    stop(playback);
            }
        });
    }

    private void backendFailed(Playback playback, Throwable error) {
        playback.failed.set(true);
        plugin.getLogger().warning("Failed to play track " + playback.music.id() + ": " + rootMessage(error));
        finishPlayback(playback);
    }

    public void stop(Block block) {
        stop(JukeboxKey.of(block.getLocation()));
    }

    /** Applies changed block settings to an active MIK playback without restarting it. */
    public void updateSettings(Block block, JukeboxSoundSettings settings) {
        java.util.Objects.requireNonNull(block, "block");
        java.util.Objects.requireNonNull(settings, "settings");
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || playback.stopped.get()) {
            return;
        }
        playback.settings = settings;
        PlaybackSession session = playback.attempt.session();
        if (session != null) {
            session.updateSettings(settings);
        }
    }

    public boolean stopAndEject(Block block) {
        stop(JukeboxKey.of(block.getLocation()));
        if (!(block.getState() instanceof Jukebox jukebox) || !jukebox.hasRecord()) {
            return false;
        }
        jukebox.stopPlaying();
        boolean ejected = jukebox.eject();
        return ejected;
    }

    /** Closes every owned resource, including backends allocated before enable succeeds. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true))
            return;
        enabled.set(false);
        ShutdownSequence shutdown = new ShutdownSequence();
        for (JukeboxKey key : List.copyOf(playbacks.keySet())) {
            shutdown.attempt("jukebox " + key, () -> stop(key));
        }
        finishedPlaybacks.clear();
        shutdown.attempt("NBS backend", nbsEngine::close);
        shutdown.attempt("audio backend", audioEngine::close);
        shutdown.finish("jukebox playback service");
    }

    public void removeWorld(World world) {
        if (world == null) {
            return;
        }
        java.util.UUID worldId = world.getUID();
        for (JukeboxKey key : List.copyOf(playbacks.keySet())) {
            if (worldId.equals(key.world())) {
                stop(key);
            }
        }
        finishedPlaybacks.keySet().removeIf(key -> worldId.equals(key.world()));
    }

    public boolean isPlaying(Block block) {
        return playbacks.containsKey(JukeboxKey.of(block.getLocation()));
    }

    @Override
    public PlaybackStatus status(Block block) {
        return snapshot(block).status();
    }

    @Override
    public boolean seekable(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        return playback != null && !playback.stopped.get() && !playback.finishing.get()
                && playback.experienceMode == JukeboxExperienceMode.MUSIC
                && playback.attempt.session() != null && playback.attempt.session().canSeek()
                && playback.music.details().audio().duration() != null
                && playbacks.values()
                           .stream()
                           .filter(active -> active.attempt.group() == playback.attempt.group())
                           .filter(active -> !active.stopped.get() && !active.finishing.get())
                           .allMatch(active
                                   -> active.attempt.session() != null && active.attempt.session().canSeek());
    }

    /** Moves every active jukebox sharing this song clock to the requested position. */
    public boolean seek(Block block, String trackId, long positionMillis) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || !playback.music.id().equals(trackId) || !seekable(block))
            return false;
        long durationMillis;
        try {
            durationMillis = playback.music.details().audio().duration().toMillis();
        } catch (ArithmeticException exception) {
            return false;
        }
        if (durationMillis <= 0L)
            return false;
        List<Playback> group = playbacks.values()
                                       .stream()
                                       .filter(active -> active.attempt.group() == playback.attempt.group())
                                       .filter(active -> !active.stopped.get() && !active.finishing.get())
                                       .toList();
        long target = Math.clamp(positionMillis, 0L, durationMillis - 1L);
        playback.attempt.group().seekTo(target);
        for (Playback active : group) {
            PlaybackSession session = active.attempt.session();
            if (session != null && session.canSeek())
                session.seekTo(target);
        }
        return true;
    }

    /** Pauses or resumes all jukeboxes sharing this song clock. */
    public boolean setPaused(Block block, String trackId, boolean paused) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || !playback.music.id().equals(trackId)
                || playback.experienceMode != JukeboxExperienceMode.MUSIC || playback.stopped.get()
                || playback.finishing.get())
            return false;
        PlaybackSession session = playback.attempt.session();
        if (session == null || session.status() != (paused ? PlaybackStatus.PLAYING : PlaybackStatus.PAUSED))
            return false;
        playback.attempt.group().setPaused(paused);
        for (Playback active : playbacks.values()) {
            if (active.attempt.group() != playback.attempt.group() || active.stopped.get()
                    || active.finishing.get())
                continue;
            PlaybackSession member = active.attempt.session();
            active.attempt.countingPaused(paused);
            if (member != null)
                member.setPaused(paused);
        }
        return true;
    }

    @Override
    public JukeboxPlaybackSnapshot snapshot(Block block) {
        JukeboxKey key = JukeboxKey.of(block.getLocation());
        Playback playback = playbacks.get(key);
        if (playback == null || playback.stopped.get()) {
            FinishedPlayback finished = finishedPlaybacks.get(key);
            if (finished != null && block.getState() instanceof Jukebox jukebox
                    && finished.trackId().equals(MusicDiscKeys.trackId(jukebox.getRecord()))) {
                return new JukeboxPlaybackSnapshot(PlaybackStatus.FINISHED, finished.positionMillis());
            }
            finishedPlaybacks.remove(key);
            return new JukeboxPlaybackSnapshot(PlaybackStatus.STOPPED, 0L);
        }
        PlaybackSession session = playback.attempt.session();
        return session == null ? new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, 0L)
                               : new JukeboxPlaybackSnapshot(session.status(), session.positionMillis());
    }

    @Override
    public Optional<JukeboxExperienceMode> activeExperienceMode(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        return playback == null || playback.stopped.get() ? Optional.empty()
                                                          : Optional.of(playback.experienceMode);
    }

    @Override
    public JukeboxRhythmReadiness rhythmReadiness(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || playback.stopped.get()) {
            return JukeboxRhythmReadiness.UNAVAILABLE;
        }
        if (playback.experienceMode == JukeboxExperienceMode.RHYTHM && playback.rhythmParticipants.isEmpty()
                && playback.attempt.session() == null) {
            return JukeboxRhythmReadiness.WAITING_FOR_PLAYER;
        }
        if (!playback.rhythmTimeline.complete()) {
            return JukeboxRhythmReadiness.PREPARING;
        }
        return playback.rhythmTimeline.playable() ? JukeboxRhythmReadiness.READY
                                                  : JukeboxRhythmReadiness.NO_BEATS;
    }

    /** Exposes one coherent playback clock and its automatically generated chart. */
    @Override
    public Optional<RhythmPlaybackSnapshot> rhythmPlayback(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || playback.stopped.get()
                || playback.experienceMode != JukeboxExperienceMode.RHYTHM) {
            return Optional.empty();
        }
        PlaybackSession session = playback.attempt.session();
        PlaybackStatus currentStatus = session == null ? PlaybackStatus.LOADING : session.status();
        long position = session == null ? 0L : session.positionMillis();
        RhythmPlaybackState rhythmState = switch (currentStatus) {
            case LOADING -> RhythmPlaybackState.LOADING;
            case PLAYING -> RhythmPlaybackState.PLAYING;
            case PAUSED, FINISHED, STOPPED -> RhythmPlaybackState.STOPPED;
        };
        return Optional.of(new RhythmPlaybackSnapshot(playback.attempt.id(), playback.music,
                playback.music.target() instanceof TrackTarget.NbsFile ? RhythmAudioChannel.MINECRAFT
                                                                       : RhythmAudioChannel.PLASMO_VOICE,
                position, rhythmState, playback.rhythmTimeline));
    }

    /** Silences all managed jukebox backends only for this player. */
    @Override
    public SilenceLease silenceFor(Player player) {
        return audioAudience.silence(java.util.Objects.requireNonNull(player, "player").getUniqueId());
    }

    private void finishPlayback(Playback playback) {
        if (!playback.finishing.compareAndSet(false, true)) {
            return;
        }
        playback.attempt.close();

        if (!enabled.get()) {
            removePlayback(playback);
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!removePlayback(playback)) {
                    return;
                }
                if (!playback.location.isChunkLoaded())
                    return;
                Block block = playback.location.getBlock();
                if (!playback.digital && block.getState() instanceof Jukebox jukebox
                        && hasPlaybackDisc(jukebox, playback)) {
                    jukebox.stopPlaying();
                    if (playback.failed.get()) {
                        removeFailedDisc(jukebox);
                    } else {
                        jukebox.update(true, false);
                        long position = playback.attempt.session() == null
                                ? 0L
                                : playback.attempt.session().positionMillis();
                        java.time.Duration duration = playback.music.details().audio().duration();
                        if (duration != null) {
                            try {
                                position = Math.max(position, duration.toMillis());
                            } catch (ArithmeticException ignored) {
                                // Keep the backend's final position.
                            }
                        }
                        finishedPlaybacks.put(
                                playback.key, new FinishedPlayback(playback.music.id(), position));
                    }
                }

                notifyFailure(playback);
                completionListener.ended(playback.location.clone(), playback.music, playback.failed.get());
            });
        } catch (IllegalStateException ignored) {
            removePlayback(playback);
            // Plugin shutdown won the race after the enabled check.
        }
    }

    private void stop(JukeboxKey key) {
        finishedPlaybacks.remove(key);
        Playback playback = playbacks.remove(key);
        if (playback == null)
            return;
        recordSilencer.playbackStopped(playback.location);
        playback.stopped.set(true);
        recordPlaybackIfQualified(playback, playback.attempt, false);
        playback.attempt.close();
    }

    private void stop(Playback playback) {
        if (!playbacks.remove(playback.key, playback)) {
            return;
        }
        recordSilencer.playbackStopped(playback.location);
        finishedPlaybacks.remove(playback.key);
        playback.stopped.set(true);
        recordPlaybackIfQualified(playback, playback.attempt, false);
        playback.attempt.close();
    }

    private void notifyStarted(Playback playback) {
        if (!playback.digital)
            physicalStarted.started(playback.location.clone(), playback.music, playback.requestingPlayer);
        try {
            playback.onStarted.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Music playback start callback failed: " + exception.getMessage());
        }
    }

    private void recordPlaybackIfQualified(
            Playback playback, PlaybackAttempt attempt, boolean finishedNaturally) {
        if (playback.failed.get()
                || !attempt.claimPlaybackRecord(
                        finishedNaturally, playback.music.details().audio().duration()))
            return;
        try {
            playbackRecorder.accept(playback.music);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to record music playback: " + exception.getMessage());
        }
    }

    private boolean hasPlaybackDisc(Jukebox jukebox, Playback playback) {
        return playback.digital ? !jukebox.hasRecord()
                                : playback.music.id().equals(MusicDiscKeys.trackId(jukebox.getRecord()));
    }

    private static void removeFailedDisc(Jukebox jukebox) {
        // Failed media keeps the real inventory record for a manual retry or ejection.
        jukebox.stopPlaying();
        jukebox.update(true, false);
    }

    private void notifyFailure(Playback playback) {
        if (!playback.failed.get() || playback.requestingPlayer == null
                || !playback.failureNotified.compareAndSet(false, true)) {
            return;
        }
        notifier.playbackFailed(playback.requestingPlayer, playback.music);
    }

    private boolean removePlayback(Playback playback) {
        if (!playbacks.remove(playback.key, playback)) {
            return false;
        }
        recordSilencer.playbackStopped(playback.location);
        return true;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    /** A selected jukebox track, retained while rhythm players wait between backend runs. */
    private static final class Playback {
        private boolean digital;
        private final JukeboxKey key;
        private final Location location;
        private final MusicTrack music;
        private final java.util.UUID requestingPlayer;
        private final JukeboxExperienceMode experienceMode;
        private final PlaybackOrigin origin;
        private final Runnable onStarted;
        private volatile JukeboxSoundSettings settings;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean finishing = new AtomicBoolean();
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicBoolean failureNotified = new AtomicBoolean();
        private final RhythmTimeline rhythmTimeline;
        private final RhythmParticipants rhythmParticipants = new RhythmParticipants();
        private volatile PlaybackAttempt attempt;

        private Playback(JukeboxKey key, Location location, MusicTrack music, java.util.UUID requestingPlayer,
                JukeboxSoundSettings settings, JukeboxExperienceMode experienceMode,
                JukeboxPlaybackGroup playbackGroup, PlaybackOrigin origin, Runnable onStarted) {
            this.key = key;
            this.location = location;
            this.music = music;
            this.requestingPlayer = requestingPlayer;
            this.settings = java.util.Objects.requireNonNull(settings, "settings");
            this.experienceMode = java.util.Objects.requireNonNull(experienceMode, "experienceMode");
            this.attempt = new PlaybackAttempt(playbackGroup);
            this.origin = origin;
            this.onStarted = java.util.Objects.requireNonNull(onStarted, "onStarted");
            this.rhythmTimeline = new RhythmTimeline(music.id());
        }
    }

    private record JukeboxKey(java.util.UUID world, int x, int y, int z) {
        private static JukeboxKey of(Location location) {
            return new JukeboxKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(),
                    location.getBlockZ());
        }
    }

    private record FinishedPlayback(String trackId, long positionMillis) {}
}
