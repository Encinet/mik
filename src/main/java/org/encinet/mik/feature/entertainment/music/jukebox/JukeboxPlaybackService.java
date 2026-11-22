package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.device.JukeboxExperienceMode;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSoundSettings;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscResolver;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscFactory;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscKeys;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricDisplayService;
import org.encinet.mik.feature.entertainment.music.lyrics.Lyrics;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricsService;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;
import org.encinet.mik.feature.entertainment.music.gateway.MusicRequestLimiter;
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
        implements RhythmPlaybackGateway, RhythmPlaybackIsolation, AutoCloseable {
    private final JavaPlugin plugin;
    private final MusicDiscResolver discResolver;
    private final MusicDiscFactory discFactory;
    private final JukeboxSettingsStore settingsStore;
    private final JukeboxPlaybackNotifier notifier;
    private final VanillaRecordSilencer recordSilencer;
    private final JukeboxAudioAudience audioAudience = new JukeboxAudioAudience();
    private final JukeboxPlaybackAudience clientAudience;
    private final AudioPlaybackEngine audioEngine;
    private final NotePlaybackEngine noteEngine;
    private final ManagedPlaybackEngine managedEngine;
    private final NativeRecordPlaybackEngine nativeEngine;
    private final LyricDisplayService lyricDisplay;
    private final MusicRequestLimiter requestLimiter;
    private final Map<JukeboxKey, Playback> playbacks = new ConcurrentHashMap<>();
    private final Map<JukeboxKey, FinishedPlayback> finishedPlaybacks = new ConcurrentHashMap<>();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Map<Location, Set<UUID>> outputListeners = Map.of();
    private volatile BiPredicate<Location, UUID> audiencePolicy = (_, _) -> true;
    private boolean audienceRefreshQueued;
    private volatile PhysicalStarted physicalStarted = (_, _, _) -> {};
    @FunctionalInterface
    public interface PhysicalStarted {
        void started(Location location, MusicTrack track, UUID actor);
    }
    public void setPhysicalStarted(PhysicalStarted listener) {
        physicalStarted = listener;
    }
    /** Identity changes when the same physical disc starts another backend attempt. */
    public record PhysicalPlayback(UUID id, MusicTrack track, String mediaVersion) {
        public PhysicalPlayback { java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(track); }
    }
    public PhysicalPlayback physicalPlayback(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        return playback != null && !playback.digital && !playback.stopped.get()
                ? new PhysicalPlayback(playback.attempt.id(), playback.music,
                        playback.attempt.session() == null ? null : playback.attempt.session().mediaVersion()) : null;
    }
    private volatile CompletionListener completionListener = (_, _, _) -> {};
    @FunctionalInterface
    public interface CompletionListener {
        void ended(Location location, MusicTrack track, Throwable failure);
    }
    public void setCompletionListener(CompletionListener listener) {
        completionListener = listener;
    }
    public void setOutputAudience(BiPredicate<Location, UUID> filter) {
        audiencePolicy = filter;
    }

    public LyricsService.Playback lyrics(Block block, UUID player, boolean mayControl) {
        var playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || !playback.attempt.active() || !mayControl
                && !outputListeners.getOrDefault(playback.location, Set.of()).contains(player)) return null;
        return playback.attempt.group().lyrics();
    }

    public Lyrics.Role auxiliaryCaptions(UUID player) { return lyricDisplay.auxiliary(player); }
    public void auxiliaryCaptions(UUID player, Lyrics.Role role) { lyricDisplay.auxiliary(player, role); }
    public void clearListener(UUID player) { lyricDisplay.quit(player); }

    public void refreshOutputAudience() {
        if (!enabled.get()) return;
        Map<Location, Set<UUID>> listeners = new HashMap<>();
        Map<JukeboxPlaybackGroup, Map<UUID, NearestOutput>> groups = new HashMap<>();
        for (Playback playback : playbacks.values()) {
            if (playback.stopped.get() || playback.finishing.get() || playback.attempt.session() == null) continue;
            Location location = playback.location;
            listeners.put(location, new HashSet<>());
            if (playback.settings.volumePercent() == 0 || !location.isChunkLoaded()) continue;
            var candidates = groups.computeIfAbsent(playback.attempt.group(), _ -> new HashMap<>());
            double maximum = playback.settings.rangeBlocks();
            for (Player player : location.getWorld().getNearbyPlayers(location, maximum)) {
                UUID id = player.getUniqueId();
                if (!audioAudience.canHear(id) || !audiencePolicy.test(location, id)
                        || !clientAudience.canHearBackend(player, playback.attempt.session().outputKind())) continue;
                double distance = player.getLocation().distanceSquared(location);
                if (distance > maximum * maximum) continue;
                NearestOutput current = candidates.get(id);
                if (current == null || distance < current.distance()) {
                    candidates.put(id, new NearestOutput(location, distance));
                }
            }
        }
        for (var group : groups.values()) {
            group.forEach((listener, output) -> listeners.get(output.location()).add(listener));
        }
        listeners.replaceAll((_, ids) -> Set.copyOf(ids));
        outputListeners = Map.copyOf(listeners);
        lyricDisplay.refresh(outputListeners);
    }

    private void refreshAudienceSoon() {
        if (audienceRefreshQueued || !enabled.get()) return;
        audienceRefreshQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            audienceRefreshQueued = false;
            refreshOutputAudience();
        });
    }

    private record NearestOutput(Location location, double distance) { }

    public static final class ProgramPlayback {
        private final JukeboxPlaybackGroup group = new JukeboxPlaybackGroup();
        private final Set<Playback> outputs = ConcurrentHashMap.newKeySet();

        public boolean paused() { return group.paused(); }

        public String mediaVersion() {
            return group.resumeVersion();
        }

        public long recoveryPosition() { return group.recoveryPosition(); }

        public void restore(String version, long position) {
            group.pinVersion(version);
            group.seekTo(position);
        }

        public boolean setPaused(boolean paused) {
            if (!paused && group.paused()) {
                group.setPaused(false);
                return true;
            }
            return outputs.stream().anyMatch(output -> pause(output, paused));
        }

        public JukeboxPlaybackSnapshot snapshot() {
            return outputs.isEmpty() ? group.resumeVersion() == null ? null
                    : new JukeboxPlaybackSnapshot(PlaybackStatus.RESUMABLE, recoveryPosition()) : group.snapshot();
        }

        public boolean seek(long positionMillis) {
            return outputs.stream().anyMatch(output -> seekPlayback(output, positionMillis));
        }
    }

    public boolean playProgram(
            Player player, Jukebox jukebox, MusicTrack track, ProgramPlayback program, Runnable started) {
        return startTrack(player, jukebox, track, true, discFactory.createPersistentDisc(track),
                PlaybackOrigin.AUTOMATIC, started, program);
    }
    /** The source keeps its one real disc; other outputs share its clock with empty slots. */
    public boolean playRecordOutput(Player player, Jukebox jukebox, MusicTrack track, ProgramPlayback program,
                                    boolean source, PlaybackOrigin origin) {
        return startTrack(player, jukebox, track, !source, null, origin, () -> {}, program);
    }

    public JukeboxPlaybackService(JavaPlugin plugin, PlasmoVoiceServer voiceServer,
            MusicDiscResolver discResolver, MusicDiscFactory discFactory, MusicMedia media, JukeboxPlaybackNotifier notifier,
            VanillaRecordSilencer recordSilencer, JukeboxSettingsStore settingsStore,
            LyricDisplayService lyricDisplay,
            MusicRequestLimiter requestLimiter) {
        this.plugin = plugin;
        this.discResolver = discResolver;
        this.discFactory = java.util.Objects.requireNonNull(discFactory, "discFactory");
        this.notifier = notifier;
        this.recordSilencer = recordSilencer;
        this.settingsStore = java.util.Objects.requireNonNull(settingsStore, "settingsStore");
        this.lyricDisplay = java.util.Objects.requireNonNull(lyricDisplay, "lyricDisplay");
        this.requestLimiter = java.util.Objects.requireNonNull(requestLimiter, "requestLimiter");

        this.clientAudience = new JukeboxPlaybackAudience(voiceServer);
        this.audioEngine = new AudioPlaybackEngine(plugin, voiceServer, media, audioAudience::canHear);
        this.noteEngine = new NotePlaybackEngine(plugin, audioAudience::canHear);
        this.managedEngine = new ManagedPlaybackEngine(task -> Bukkit.getScheduler().runTask(plugin, task),
                media, audioEngine::create, noteEngine::create);
        this.nativeEngine = new NativeRecordPlaybackEngine(plugin, audioAudience::canHear);
        this.audioEngine.setOutputAudience((location, id) -> audiencePolicy.test(location, id)
                && outputListeners.getOrDefault(location, Set.of()).contains(id));
        this.audioEngine.setAudiencePresence(location -> !outputListeners.getOrDefault(location, Set.of()).isEmpty());
        this.noteEngine.setOutputAudience(location -> outputListeners.getOrDefault(location, Set.of()));
        this.nativeEngine.setListenerPolicy((location, id) -> audiencePolicy.test(location, id));
        this.nativeEngine.setOutputAudience(location -> outputListeners.getOrDefault(location, Set.of()));
    }

    public void enable() {
        if (closed.get())
            throw new IllegalStateException("Jukebox playback service is closed");
        enabled.set(true);
    }

    /** Starts an inserted vanilla or signed custom record. */
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
        return startTrack(player, jukebox, music, false, null, origin, () -> {}, null);
    }

    private boolean startTrack(Player player, Jukebox jukebox, MusicTrack music, boolean replaceRecord, ItemStack replacement,
            PlaybackOrigin origin, Runnable onStarted, ProgramPlayback program) {
        java.util.Objects.requireNonNull(origin, "origin");
        if (!enabled.get())
            return false;
        Location location = jukebox.getLocation().clone();
        JukeboxKey key = JukeboxKey.of(location);
        if (!replaceRecord && !matchesRecord(jukebox,music.id())) {
            return false;
        }
        JukeboxSoundSettings settings = settingsStore.read(jukebox);
        JukeboxExperienceMode experienceMode = settingsStore.readExperienceMode(jukebox);
        if (program == null && experienceMode == JukeboxExperienceMode.MUSIC
                && overlapsActivePlayback(key, location, settings)) {
            if (player != null) {
                notifier.overlappingPlayback(player);
            }
            return false;
        }
        if (origin == PlaybackOrigin.PLAYER && player != null && music.target() instanceof TrackTarget.Managed) {
            MusicRequestLimiter.Decision decision = requestLimiter.tryAcquire(
                    player.getUniqueId(), MusicRequestLimiter.Operation.PLAYBACK);
            if (!decision.allowed()) {
                notifier.requestRateLimited(player, decision.retryAfterSeconds());
                return false;
            }
        }
        stop(key);
        finishedPlaybacks.remove(key);
        Playback playback = new Playback(key, location, music, player == null ? null : player.getUniqueId(),
                settings, experienceMode, program, origin, onStarted);
        playback.digital = replaceRecord;
        playback.emptySlot = replaceRecord && replacement == null;
        playbacks.put(key, playback);
        if (program != null) program.outputs.add(playback);
        prepareJukebox(jukebox, replaceRecord, replacement);

        if (playback.experienceMode == JukeboxExperienceMode.MUSIC) {
            recordSilencer.playbackStarted(location);
            startBackend(playback);
        }
        return playback.failure == null;
    }

    /** Replace a program's item in place; synchronized physical-record followers keep empty slots. */
    static void prepareJukebox(Jukebox jukebox, boolean replaceRecord, ItemStack replacement) {
        jukebox.stopPlaying();
        if (replaceRecord) {
            jukebox.setRecord(replacement);
            if (replacement != null) jukebox.stopPlaying();
        }
        jukebox.update(true, false);
    }

    private boolean overlapsActivePlayback(JukeboxKey key, Location location, JukeboxSoundSettings settings) {
        return playbacks.values().stream().anyMatch(active -> !active.key.equals(key)
                && !active.stopped.get() && !active.finishing.get()
                && active.experienceMode == JukeboxExperienceMode.MUSIC
                && JukeboxAudibleField.overlaps(location, settings, active.location, active.settings));
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
        if (playback.failure != null || playbacks.get(playback.key) != playback) {
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
        playback.rhythmTimeline = playback.attempt.group().timeline(playback.music.id());
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
            refreshAudienceSoon();
            attempt.prepare(() -> {
                PlaybackCallbacks callbacks = callbacks(playback, attempt);
                if (playback.music.target() instanceof TrackTarget.NativeRecord)
                    return nativeEngine.create(playback.location,playback.music,playback.settings,callbacks,attempt.group());
                return managedEngine.create(playback.location, playback.music, playback.settings, callbacks,
                        playback.rhythmTimeline, playback.experienceMode, attempt.group(), this::refreshAudienceSoon);
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
                    String version = session.mediaVersion();
                    attempt.group().pinVersion(version);
                    if (version != null) {
                        var document = attempt.group().lyrics(() -> lyricDisplay.document(version));
                        attempt.attachLyrics(lyricDisplay.start(playback.location, document, session::positionMillis));
                    }
                }
                if (firstStart) {
                    if (playback.origin == PlaybackOrigin.PLAYER) {
                        notifier.playbackInaudible(playback.requestingPlayer, session == null ? PlaybackSession.OutputKind.UNRESOLVED : session.outputKind());
                    }
                    if (attempt.group().claimStartedAnnouncement()) {
                        notifier.broadcastStarted(playback.location, playback.music.details().title(),
                                playback.music, session == null ? PlaybackSession.OutputKind.UNRESOLVED : session.outputKind(), playback.settings.rangeBlocks());
                    }
                }
            }

            @Override
            public void failed(Throwable error) {
                if (isCurrentAttempt(playback, attempt))
                    backendFailed(playback, error);
            }

            @Override
            public void finished() {
                if (!isCurrentAttempt(playback, attempt))
                    return;
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
        playback.failure = error;
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
        refreshAudienceSoon();
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
        shutdown.attempt("managed playback", managedEngine::close);
        shutdown.attempt("captions", lyricDisplay::close);
        shutdown.attempt("note output", noteEngine::close);
        shutdown.attempt("native records", nativeEngine::close);
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

    public boolean seek(Block block, UUID attemptId, long positionMillis) {
        return seekPlayback(physicalAttempt(block, attemptId), positionMillis);
    }

    public boolean setPaused(Block block, UUID attemptId, boolean paused) {
        return pause(physicalAttempt(block, attemptId), paused);
    }

    private Playback physicalAttempt(Block block, UUID attemptId) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        return playback != null && playback.program == null && !playback.digital
                && playback.attempt.id().equals(attemptId) ? playback : null;
    }

    private static PlaybackSession activeMusicSession(Playback playback) {
        return playback == null || playback.experienceMode != JukeboxExperienceMode.MUSIC
                || playback.stopped.get() || playback.finishing.get() ? null : playback.attempt.session();
    }

    private static boolean seekPlayback(Playback playback, long positionMillis) {
        var session = activeMusicSession(playback);
        var sample = session == null ? null : session.snapshot();
        if (sample == null || !sample.seekable()) return false;
        playback.attempt.group().seekTo(Math.clamp(positionMillis, 0L, sample.durationMillis() - 1L));
        return true;
    }

    private static boolean pause(Playback playback, boolean paused) {
        PlaybackSession session = activeMusicSession(playback);
        if (session == null || playback.music.target() instanceof TrackTarget.NativeRecord)
            return false;
        PlaybackStatus status = session.status();
        if (!status.hasLiveTransport())
            return false;
        playback.attempt.group().setPaused(paused);
        return true;
    }

    public JukeboxPlaybackSnapshot snapshot(Block block) {
        JukeboxKey key = JukeboxKey.of(block.getLocation());
        Playback playback = playbacks.get(key);
        if (playback == null || playback.stopped.get()) {
            FinishedPlayback finished = finishedPlaybacks.get(key);
            if (finished != null && block.getState() instanceof Jukebox jukebox
                    && matchesRecord(jukebox,finished.trackId())) {
                return new JukeboxPlaybackSnapshot(PlaybackStatus.FINISHED, finished.positionMillis());
            }
            finishedPlaybacks.remove(key);
            return new JukeboxPlaybackSnapshot(PlaybackStatus.STOPPED, 0L);
        }
        PlaybackSession session = playback.attempt.session();
        return session == null ? new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, 0L)
                               : session.snapshot();
    }

    public Optional<JukeboxExperienceMode> activeExperienceMode(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        return playback == null || playback.stopped.get() ? Optional.empty()
                                                          : Optional.of(playback.experienceMode);
    }

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
        var clock = session == null ? new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, 0L) : session.snapshot();
        RhythmPlaybackState rhythmState = switch (clock.status()) {
            case LOADING -> RhythmPlaybackState.LOADING;
            case PLAYING -> RhythmPlaybackState.PLAYING;
            case BUFFERING -> RhythmPlaybackState.BUFFERING;
            case PAUSED, FINISHED, STOPPED, RESUMABLE -> RhythmPlaybackState.STOPPED;
        };
        return Optional.of(new RhythmPlaybackSnapshot(playback.attempt.id(), playback.music,
                session != null && session.outputKind() == PlaybackSession.OutputKind.NOTE_EVENTS ? RhythmAudioChannel.MINECRAFT
                                                                       : RhythmAudioChannel.PLASMO_VOICE,
                clock.positionMillis(), rhythmState, playback.rhythmTimeline));
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
                if (!playback.digital && playback.location.isChunkLoaded()
                        && playback.location.getBlock().getState() instanceof Jukebox jukebox
                        && hasPlaybackDisc(jukebox, playback)) {
                    jukebox.stopPlaying();
                    jukebox.update(true, false);
                    if (playback.failure == null) {
                        long position = playback.attempt.session() == null
                                ? 0L
                                : Math.max(playback.attempt.session().positionMillis(), playback.attempt.session().snapshot().durationMillis());
                        finishedPlaybacks.put(
                                playback.key, new FinishedPlayback(playback.music.id(), position));
                    }
                }

                notifyFailure(playback);
                completionListener.ended(playback.location.clone(), playback.music, playback.failure);
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
        stopRemoved(playback);
    }

    private void stop(Playback playback) {
        if (!playbacks.remove(playback.key, playback)) {
            return;
        }
        finishedPlaybacks.remove(playback.key);
        stopRemoved(playback);
    }

    private void stopRemoved(Playback playback) {
        playback.stopped.set(true);
        detachOutput(playback);
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

    private boolean hasPlaybackDisc(Jukebox jukebox, Playback playback) {
        return playback.emptySlot ? !jukebox.hasRecord() : matchesRecord(jukebox,playback.music.id());
    }

    private boolean matchesRecord(Jukebox jukebox, String track) {
        var record = jukebox.getRecord();
        String custom = MusicDiscKeys.trackId(record);
        if (custom != null) return custom.equals(track);
        var playable = record.getData(io.papermc.paper.datacomponent.DataComponentTypes.JUKEBOX_PLAYABLE);
        return playable != null && track.equals("record:"+playable.jukeboxSong().key().asString());
    }

    private void notifyFailure(Playback playback) {
        if (playback.failure == null || playback.requestingPlayer == null
                || !playback.failureNotified.compareAndSet(false, true)) {
            return;
        }
        notifier.playbackFailed(playback.requestingPlayer, playback.music);
    }

    private boolean removePlayback(Playback playback) {
        if (!playbacks.remove(playback.key, playback)) {
            return false;
        }
        detachOutput(playback);
        return true;
    }

    private void detachOutput(Playback playback) {
        if (playback.program != null) playback.program.outputs.remove(playback);
        recordSilencer.playbackStopped(playback.location);
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
        private boolean emptySlot;
        private final JukeboxKey key;
        private final Location location;
        private final MusicTrack music;
        private final java.util.UUID requestingPlayer;
        private final JukeboxExperienceMode experienceMode;
        private final PlaybackOrigin origin;
        private final Runnable onStarted;
        private final ProgramPlayback program;
        private volatile JukeboxSoundSettings settings;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean finishing = new AtomicBoolean();
        private volatile Throwable failure;
        private final AtomicBoolean failureNotified = new AtomicBoolean();
        private RhythmTimeline rhythmTimeline;
        private final RhythmParticipants rhythmParticipants = new RhythmParticipants();
        private volatile PlaybackAttempt attempt;

        private Playback(JukeboxKey key, Location location, MusicTrack music, java.util.UUID requestingPlayer,
                JukeboxSoundSettings settings, JukeboxExperienceMode experienceMode,
                ProgramPlayback program, PlaybackOrigin origin, Runnable onStarted) {
            this.key = key;
            this.location = location;
            this.music = music;
            this.requestingPlayer = requestingPlayer;
            this.settings = java.util.Objects.requireNonNull(settings, "settings");
            this.experienceMode = java.util.Objects.requireNonNull(experienceMode, "experienceMode");
            this.program = program;
            JukeboxPlaybackGroup playbackGroup = program == null ? new JukeboxPlaybackGroup() : program.group;
            this.attempt = new PlaybackAttempt(playbackGroup);
            this.origin = origin;
            this.onStarted = java.util.Objects.requireNonNull(onStarted, "onStarted");
            this.rhythmTimeline = playbackGroup.timeline(music.id());
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
