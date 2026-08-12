package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.catalog.nbs.NbsInstruments;
import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsNoteType;
import org.encinet.mik.module.music.catalog.nbs.NbsParser;
import org.encinet.mik.module.music.catalog.nbs.NbsSong;
import org.encinet.mik.module.music.rhythm.analysis.NbsRhythmExtractor;
import org.encinet.mik.module.music.rhythm.analysis.RhythmExtractor;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Loads NBS files off-thread and plays their note-block sounds on the main thread. */
final class NbsPlaybackEngine implements AutoCloseable {

    private final Scheduler scheduler;
    private final LocalMediaPreparer mediaPreparer;
    private final NbsParser parser;
    private final RhythmExtractor<NbsSong> rhythmExtractor;
    private final ExecutorService parsingExecutor;
    private final LongSupplier nanoClock;
    private final Predicate<UUID> audibleToPlayer;
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    NbsPlaybackEngine(JavaPlugin plugin) {
        this(plugin, ignored -> true);
    }

    NbsPlaybackEngine(JavaPlugin plugin, Predicate<UUID> audibleToPlayer) {
        this(new BukkitScheduler(plugin), new LocalMediaPreparer(), new NbsParser(),
                Executors.newVirtualThreadPerTaskExecutor(), NbsRhythmExtractor.INSTANCE,
                System::nanoTime, audibleToPlayer);
    }

    NbsPlaybackEngine(Scheduler scheduler, LocalMediaPreparer mediaPreparer,
                       NbsParser parser, ExecutorService parsingExecutor) {
        this(scheduler, mediaPreparer, parser, parsingExecutor,
                NbsRhythmExtractor.INSTANCE, System::nanoTime, ignored -> true);
    }

    NbsPlaybackEngine(Scheduler scheduler, LocalMediaPreparer mediaPreparer,
                      NbsParser parser, ExecutorService parsingExecutor,
                      RhythmExtractor<NbsSong> rhythmExtractor) {
        this(scheduler, mediaPreparer, parser, parsingExecutor, rhythmExtractor,
                System::nanoTime, ignored -> true);
    }

    NbsPlaybackEngine(Scheduler scheduler, LocalMediaPreparer mediaPreparer,
                      NbsParser parser, ExecutorService parsingExecutor,
                      RhythmExtractor<NbsSong> rhythmExtractor,
                      LongSupplier nanoClock) {
        this(scheduler, mediaPreparer, parser, parsingExecutor, rhythmExtractor,
                nanoClock, ignored -> true);
    }

    NbsPlaybackEngine(Scheduler scheduler, LocalMediaPreparer mediaPreparer,
                      NbsParser parser, ExecutorService parsingExecutor,
                      RhythmExtractor<NbsSong> rhythmExtractor,
                      LongSupplier nanoClock,
                      Predicate<UUID> audibleToPlayer) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.mediaPreparer = Objects.requireNonNull(mediaPreparer, "mediaPreparer");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.parsingExecutor = Objects.requireNonNull(parsingExecutor, "parsingExecutor");
        this.rhythmExtractor = Objects.requireNonNull(rhythmExtractor, "rhythmExtractor");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.audibleToPlayer = Objects.requireNonNull(
                audibleToPlayer, "audibleToPlayer");
    }

    PlaybackSession create(Location location, TrackTarget.NbsFile target,
                           JukeboxSoundSettings settings, PlaybackCallbacks callbacks,
                           RhythmTimeline rhythmTimeline,
                           JukeboxPlaybackGroup playbackGroup) {
        if (closed.get()) {
            throw new IllegalStateException("NBS playback backend is closed");
        }
        Session session = new Session(location.clone(), target, settings, callbacks,
                rhythmTimeline, playbackGroup);
        sessions.add(session);
        return session;
    }

    PlaybackSession create(Location location, TrackTarget.NbsFile target,
                           JukeboxSoundSettings settings, PlaybackCallbacks callbacks) {
        return create(location, target, settings, callbacks,
                new RhythmTimeline(target.path().toString()),
                new JukeboxPlaybackGroup());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List.copyOf(sessions).forEach(Session::stop);
        sessions.clear();
        parsingExecutor.shutdownNow();
    }

    private final class Session implements PlaybackSession {
        private final Location location;
        private final TrackTarget.NbsFile target;
        private final PlaybackCallbacks callbacks;
        private final RhythmTimeline rhythmTimeline;
        private final JukeboxPlaybackGroup playbackGroup;
        private final NbsSoundLedger soundLedger = new NbsSoundLedger();
        private final AtomicBoolean preparationStarted = new AtomicBoolean();
        private final AtomicBoolean playbackRequested = new AtomicBoolean();
        private final AtomicBoolean outputStarted = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile JukeboxSoundSettings settings;
        private volatile PlaybackStatus status = PlaybackStatus.LOADING;
        private volatile long playbackPositionMillis;
        private volatile long finalPositionMillis;
        private volatile CompletableFuture<NbsSong> parsing;
        private volatile NbsSong preparedSong;
        private volatile NbsPlaybackCursor cursor;
        private volatile ScheduledPlayback task;

        private Session(Location location, TrackTarget.NbsFile target,
                        JukeboxSoundSettings settings, PlaybackCallbacks callbacks,
                        RhythmTimeline rhythmTimeline,
                        JukeboxPlaybackGroup playbackGroup) {
            this.location = location;
            this.target = Objects.requireNonNull(target, "target");
            this.settings = Objects.requireNonNull(settings, "settings");
            this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
            this.rhythmTimeline = Objects.requireNonNull(rhythmTimeline, "rhythmTimeline");
            this.playbackGroup = Objects.requireNonNull(
                    playbackGroup, "playbackGroup");
        }

        @Override
        public void prepare() {
            if (!preparationStarted.compareAndSet(false, true) || terminal.get()) {
                return;
            }
            if (closed.get() || !callbacks.isValid()) {
                cancel();
                return;
            }
            try {
                parsing = CompletableFuture.supplyAsync(this::loadSong, parsingExecutor);
                parsing.whenComplete((song, error) -> completeParsingOnMainThread(song, error));
            } catch (RuntimeException exception) {
                fail(exception);
            }
        }

        @Override
        public void start() {
            if (terminal.get()) return;
            playbackRequested.set(true);
            prepare();
            startPreparedSong();
        }

        private NbsSong loadSong() {
            try {
                NbsSong song = parser.parse(mediaPreparer.prepare(target));
                if (!rhythmTimeline.complete()) {
                    rhythmExtractor.extract(song, rhythmTimeline);
                }
                return song;
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }

        private void completeParsingOnMainThread(NbsSong song, Throwable error) {
            if (terminal.get() || closed.get()) {
                return;
            }
            try {
                scheduler.runOnMainThread(() -> completeParsing(song, error));
            } catch (RuntimeException exception) {
                fail(error == null ? exception : error);
            }
        }

        private void completeParsing(NbsSong song, Throwable error) {
            if (terminal.get()) {
                return;
            }
            if (closed.get() || !callbacks.isValid()) {
                cancel();
                return;
            }
            if (error != null) {
                fail(unwrap(error));
                return;
            }
            preparedSong = Objects.requireNonNull(song, "song");
            startPreparedSong();
        }

        private void startPreparedSong() {
            NbsSong song = preparedSong;
            if (!playbackRequested.get() || song == null || terminal.get()
                    || closed.get() || !outputStarted.compareAndSet(false, true)) {
                return;
            }
            try {
                if (!callbacks.isValid()) {
                    cancel();
                    return;
                }
                cursor = new NbsPlaybackCursor(song,
                        playbackGroup.synchronizedPositionMillis());
                NbsPlaybackCursor.PollResult initial = cursor.poll(nanoClock.getAsLong());
                playbackPositionMillis = initial.positionMillis();
                playbackGroup.publishPositionMillis(playbackPositionMillis);
                task = scheduler.scheduleEveryTick(this::tick);
                status = PlaybackStatus.PLAYING;
                playNotes(location, initial.notes(), settings, audibleToPlayer,
                        soundLedger);
                callbacks.started();
            } catch (RuntimeException exception) {
                fail(exception);
            }
        }

        private void tick() {
            if (terminal.get()) {
                return;
            }
            if (closed.get() || !callbacks.isValid()) {
                cancel();
                return;
            }
            try {
                NbsPlaybackCursor.PollResult result = cursor.poll(nanoClock.getAsLong());
                playbackPositionMillis = result.positionMillis();
                playbackGroup.publishPositionMillis(playbackPositionMillis);
                playNotes(location, result.notes(), settings, audibleToPlayer,
                        soundLedger);
                if (result.finished()) {
                    finish();
                }
            } catch (RuntimeException exception) {
                fail(exception);
            }
        }

        @Override
        public PlaybackStatus status() {
            return terminal.get() ? PlaybackStatus.STOPPED : status;
        }

        @Override
        public long positionMillis() {
            if (terminal.get()) {
                return finalPositionMillis;
            }
            return playbackPositionMillis;
        }

        @Override
        public void updateSettings(JukeboxSoundSettings settings) {
            this.settings = Objects.requireNonNull(settings, "settings");
        }

        @Override
        public void stop() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            stopActiveSounds();
            cleanup();
        }

        private void fail(Throwable error) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            stopActiveSounds();
            cleanup();
            callbacks.failed(error);
        }

        private void finish() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            cleanup();
            callbacks.finished();
        }

        private void cancel() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            stopActiveSounds();
            cleanup();
            callbacks.cancelled();
        }

        private void cleanup() {
            status = PlaybackStatus.STOPPED;
            finalPositionMillis = Math.max(finalPositionMillis, playbackPositionMillis);
            CompletableFuture<NbsSong> currentParsing = parsing;
            if (currentParsing != null) {
                currentParsing.cancel(true);
            }
            ScheduledPlayback currentTask = task;
            if (currentTask != null) {
                currentTask.cancel();
            }
            sessions.remove(this);
        }

        private void stopActiveSounds() {
            World world = location.getWorld();
            if (world != null) {
                stopSounds(world, soundLedger.stopAll());
            }
        }
    }

    interface Scheduler {
        void runOnMainThread(Runnable task);

        ScheduledPlayback scheduleEveryTick(Runnable task);
    }

    interface ScheduledPlayback {
        void cancel();
    }

    private record BukkitScheduler(JavaPlugin plugin) implements Scheduler {
        private BukkitScheduler {
            Objects.requireNonNull(plugin, "plugin");
        }

        @Override
        public void runOnMainThread(Runnable task) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }

        @Override
        public ScheduledPlayback scheduleEveryTick(Runnable task) {
            var bukkitTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, task, 0L, 1L);
            return bukkitTask::cancel;
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void playNotes(Location jukeboxLocation, List<NbsNote> notes,
                                  JukeboxSoundSettings settings,
                                  Predicate<UUID> audibleToPlayer,
                                  NbsSoundLedger soundLedger) {
        World world = jukeboxLocation.getWorld();
        if (world == null || notes.isEmpty()) {
            return;
        }
        double rangeSquared = (double) settings.rangeBlocks() * settings.rangeBlocks();
        List<Player> audience = world.getPlayers().stream()
                .filter(player -> audibleToPlayer.test(player.getUniqueId()))
                .filter(player -> player.getLocation().distanceSquared(jukeboxLocation)
                        <= rangeSquared)
                .toList();
        if (audience.isEmpty()) {
            for (NbsNote note : notes) {
                if (note.type() == NbsNoteType.SOUND_STOP) {
                    stopSounds(world, soundLedger.stop(note));
                }
            }
            return;
        }
        for (NbsNote note : notes) {
            if (note.type() == NbsNoteType.SOUND_STOP) {
                stopSounds(world, soundLedger.stop(note));
                continue;
            }
            if (note.type() != NbsNoteType.SOUND) {
                continue;
            }
            float volume = NbsPlaybackVolume.volume(note, settings.volumePercent());
            if (volume == 0.0F) {
                continue;
            }
            float semitones = note.playbackPitchCents() / 100.0F;
            float pitch = (float) Math.pow(2.0, semitones / 12.0);
            pitch = Math.max(0.5F, Math.min(2.0F, pitch));
            for (Player player : audience) {
                Location listener = player.getEyeLocation();
                Location soundLocation = NbsPlaybackPosition.forListener(
                        jukeboxLocation, listener, note.panning());
                Location audibleLocation = NbsPlaybackRange.forListener(
                        soundLocation, listener, settings.rangeBlocks());
                player.playSound(audibleLocation,
                        NbsInstruments.minecraftSound(note.instrument()),
                        SoundCategory.RECORDS, volume, pitch);
                soundLedger.record(note.layer(),
                        NbsInstruments.minecraftSound(note.instrument()),
                        player.getUniqueId());
            }
        }
    }

    private static void stopSounds(
            World world, Set<NbsSoundLedger.StopRequest> requests) {
        if (requests.isEmpty()) {
            return;
        }
        java.util.Map<UUID, Player> players = world.getPlayers().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Player::getUniqueId, player -> player));
        for (NbsSoundLedger.StopRequest request : requests) {
            Player player = players.get(request.listener());
            if (player != null) {
                player.stopSound(request.sound(), SoundCategory.RECORDS);
            }
        }
    }
}
