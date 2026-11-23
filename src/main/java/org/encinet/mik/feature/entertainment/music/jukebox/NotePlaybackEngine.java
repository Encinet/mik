package org.encinet.mik.feature.entertainment.music.jukebox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSoundSettings;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia.MediaLease;
import org.encinet.mik.protocol.music.NoteEvent;
import org.encinet.mik.protocol.music.NoteScore;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.NoteRhythmExtractor;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmExtractor;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Reads normalized note events off-thread and plays their note-block sounds on the main thread. */
final class NotePlaybackEngine implements AutoCloseable {

    private final Scheduler scheduler;
    private final RhythmExtractor<NoteScore> rhythmExtractor;
    private final ExecutorService parsingExecutor;
    private final LongSupplier nanoClock;
    private final Predicate<UUID> audibleToPlayer;
    private Function<Location, Set<UUID>> audience = _ -> Set.of();
    void setOutputAudience(Function<Location, Set<UUID>> snapshot) { audience = snapshot; }
    private final Map<JukeboxPlaybackGroup, Session> sessions = new HashMap<>();
    private volatile boolean closed;

    NotePlaybackEngine(JavaPlugin plugin, Predicate<UUID> audibleToPlayer) {
        this(new BukkitScheduler(plugin),
                new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
                        Thread.ofVirtual().name("mik-note-score-", 0).factory()), NoteRhythmExtractor.INSTANCE,
                System::nanoTime, audibleToPlayer);
    }

    NotePlaybackEngine(Scheduler scheduler, ExecutorService parsingExecutor,
                      RhythmExtractor<NoteScore> rhythmExtractor,
                      LongSupplier nanoClock,
                      Predicate<UUID> audibleToPlayer) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.parsingExecutor = Objects.requireNonNull(parsingExecutor, "parsingExecutor");
        this.rhythmExtractor = Objects.requireNonNull(rhythmExtractor, "rhythmExtractor");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.audibleToPlayer = Objects.requireNonNull(
                audibleToPlayer, "audibleToPlayer");
    }

    PlaybackSession create(Location location, MediaLease lease,
                           JukeboxSoundSettings settings, PlaybackCallbacks callbacks,
                           RhythmTimeline rhythmTimeline,
                           JukeboxPlaybackGroup playbackGroup) {
        if (closed) {
            throw new IllegalStateException("Note playback backend is closed");
        }
        Session session = sessions.computeIfAbsent(playbackGroup, group -> new Session(Objects.requireNonNull(lease), rhythmTimeline, group));
        Output output = new Output(session, location.clone(), settings, callbacks);
        session.outputs.add(output);
        return output;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        List.copyOf(sessions.values()).forEach(Session::close);
        parsingExecutor.shutdownNow();
    }

    private final class Session {
        private final MediaLease lease;
        private final String version;
        private final boolean seekable;
        private final RhythmTimeline timeline;
        private final JukeboxPlaybackGroup group;
        private final Set<Output> outputs = new LinkedHashSet<>();
        private volatile boolean stopped;
        private volatile PlaybackStatus status = PlaybackStatus.LOADING;
        private volatile long position;
        private long duration;
        private CompletableFuture<NoteScore> preparing;
        private NoteScore song;
        private NotePlaybackCursor cursor;
        private ScheduledPlayback task;
        private long seekVersion;

        private Session(MediaLease lease, RhythmTimeline timeline, JukeboxPlaybackGroup group) {
            this.lease = lease;
            this.version = lease.mediaVersion();
            this.seekable = lease.descriptor().getSeekable();
            this.timeline = timeline;
            this.group = group;
            position = group.currentSeek().positionMillis();
            group.follow(this::snapshot);
        }

        private JukeboxPlaybackSnapshot snapshot() {
            return group.present(new JukeboxPlaybackSnapshot(status, position, duration, canSeek()));
        }

        private void prepare() {
            if (preparing != null || stopped) return;
            try {
                preparing = CompletableFuture.supplyAsync(() -> {
                    try {
                        var parsed = NoteMedia.read(lease);
                        if (!stopped) rhythmExtractor.extract(parsed, timeline);
                        return parsed;
                    } catch (IOException failure) {
                        throw new CompletionException(failure);
                    }
                }, parsingExecutor);
                preparing.whenComplete((ready, failure) -> {
                    if (stopped || closed) return;
                    scheduler.runOnMainThread(() -> {
                        if (stopped || closed) return;
                        if (failure != null) {
                            finish(unwrap(failure), true);
                        } else {
                            song = ready;
                            group.pinVersion(version);
                            duration = song.hasLoop() ? 0L : timeline.analyzedThroughMillis();
                            begin();
                        }
                    });
                });
            } catch (RuntimeException failure) {
                finish(failure, true);
            }
        }

        private void begin() {
            if (song == null || stopped) return;
            try {
                var attached = new ArrayList<Output>();
                for (Output output : List.copyOf(outputs)) {
                    if (!output.requested || output.attached) continue;
                    if (!valid(output)) continue;
                    output.attached = true;
                    attached.add(output);
                }
                if (attached.isEmpty()) return;
                if (cursor == null) {
                    var seek = group.currentSeek();
                    position = seek.positionMillis();
                    seekVersion = seek.version();
                    cursor = new NotePlaybackCursor(song, position);
                    task = scheduler.scheduleEveryTick(this::tick);
                    status = group.paused() ? PlaybackStatus.PAUSED : PlaybackStatus.PLAYING;
                    if (!group.paused()) emit(cursor.poll(nanoClock.getAsLong()));
                }
                attached.forEach(output -> output.callbacks.started());
            } catch (RuntimeException failure) {
                finish(failure, true);
            }
        }

        private void tick() {
            if (stopped) return;
            try {
                for (Output output : List.copyOf(outputs)) {
                    if (output.attached) valid(output);
                }
                if (stopped) return;
                switchPause(group.paused());
                seek();
                if (status != PlaybackStatus.PAUSED) emit(cursor.poll(nanoClock.getAsLong()));
            } catch (RuntimeException failure) {
                finish(failure, true);
            }
        }

        private boolean valid(Output output) {
            if (!output.stopped && !closed && output.callbacks.isValid()) return true;
            output.stop();
            output.callbacks.cancelled();
            return false;
        }

        private void emit(NotePlaybackCursor.PollResult result) {
            position = result.positionMillis();
            if (result.resetSounds()) outputs.forEach(Output::stopSounds);
            for (Output output : List.copyOf(outputs)) {
                if (output.attached) {
                    try {
                        var listeners = audience.apply(output.location);
                        playNotes(output.location, result.notes(), output.settings, listeners, audibleToPlayer, output.sounds);
                    } catch (RuntimeException failure) {
                        output.stop();
                        output.callbacks.failed(failure);
                    }
                }
            }
            if (result.finished()) finish(null, true);
        }

        private boolean canSeek() { return !stopped && cursor != null && seekable && !song.hasLoop(); }

        private void seek() {
            var requested = group.currentSeek();
            if (!canSeek() || requested.version() == seekVersion) return;
            seekVersion = requested.version();
            outputs.forEach(Output::stopSounds);
            position = requested.positionMillis();
            cursor = new NotePlaybackCursor(song, position);
        }

        private void switchPause(boolean paused) {
            if (stopped || cursor == null) return;
            if (paused && status == PlaybackStatus.PLAYING) {
                status = PlaybackStatus.PAUSED;
                outputs.forEach(Output::stopSounds);
            } else if (!paused && status == PlaybackStatus.PAUSED) {
                cursor.resume(nanoClock.getAsLong());
                status = PlaybackStatus.PLAYING;
            }
        }

        private void detach(Output output) {
            outputs.remove(output);
            if (outputs.isEmpty()) close();
        }

        private void close() { finish(null, false); }

        private void finish(Throwable failure, boolean notify) {
            if (stopped) return;
            if (sessions.remove(group, this)) group.releaseClock();
            stopped = true;
            status = failure == null && notify ? PlaybackStatus.FINISHED : PlaybackStatus.STOPPED;
            if (task != null) task.cancel();
            if (preparing != null) preparing.cancel(false);
            lease.close();
            var ending = List.copyOf(outputs);
            outputs.clear();
            for (Output output : ending) {
                output.stopped = true;
                if (failure != null || !notify) output.stopSounds();
                if (notify) {
                    if (failure == null) output.callbacks.finished();
                    else output.callbacks.failed(failure);
                }
            }
        }
    }

    private final class Output implements PlaybackSession {
        private final Session session;
        private final Location location;
        private final PlaybackCallbacks callbacks;
        private final NoteSoundLedger sounds = new NoteSoundLedger();
        private JukeboxSoundSettings settings;
        private boolean requested;
        private boolean attached;
        private boolean stopped;

        private Output(Session session, Location location, JukeboxSoundSettings settings, PlaybackCallbacks callbacks) {
            this.session = session;
            this.location = location;
            this.settings = settings;
            this.callbacks = callbacks;
        }

        @Override public String mediaVersion() { return session.version; }

        @Override public void prepare() { session.prepare(); }
        @Override public void start() {
            if (stopped || requested) return;
            requested = true;
            session.prepare();
            session.begin();
        }
        @Override public JukeboxPlaybackSnapshot snapshot() {
            var active = session.snapshot();
            return stopped && active.status() != PlaybackStatus.FINISHED ? active.withStatus(PlaybackStatus.STOPPED) : active;
        }
        @Override public void updateSettings(JukeboxSoundSettings updated) { settings = updated; }

        @Override public void stop() {
            if (stopped) return;
            stopped = true;
            stopSounds();
            session.detach(this);
        }

        private void stopSounds() {
            World world = location.getWorld();
            if (world != null) NotePlaybackEngine.stopSounds(world, sounds.stopAll());
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

    private static void playNotes(Location jukeboxLocation, List<NoteEvent> notes,
                                  JukeboxSoundSettings settings,
                                  Set<UUID> listeners,
                                  Predicate<UUID> audibleToPlayer,
                                  NoteSoundLedger soundLedger) {
        World world = jukeboxLocation.getWorld();
        if (world == null || notes.isEmpty()) {
            return;
        }
        List<Player> audience = new ArrayList<>(listeners.size());
        for (UUID id : listeners) {
            if (!audibleToPlayer.test(id)) continue;
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getWorld().equals(world)) audience.add(player);
        }
        for (var event : notes) {
            if (event.hasStop()) {
                stopSounds(world, soundLedger.stop(event.getStop()));
                continue;
            }
            if (audience.isEmpty()) continue;
            var sound = event.getSound();
            float volume = NotePlaybackVolume.volume(sound.getGain(), settings.volumePercent());
            if (volume == 0.0F) continue;
            float pitch = (float) Math.clamp(Math.pow(2.0, sound.getPitchCents() / 1200.0), 0.5, 2.0);
            String soundId = NoteOutputTone.sound(sound.getTone());
            for (Player player : audience) {
                Location listener = player.getEyeLocation();
                Location soundLocation = NotePlaybackPosition.forListener(
                        jukeboxLocation, listener, sound.getPan());
                Location audibleLocation = NotePlaybackRange.forListener(
                        soundLocation, listener, settings.rangeBlocks());
                player.playSound(audibleLocation,
                        soundId,
                        SoundCategory.RECORDS, volume, pitch);
                soundLedger.record(sound.getVoice(),
                        soundId,
                        player.getUniqueId());
            }
        }
    }

    private static void stopSounds(
            World world, Set<NoteSoundLedger.StopRequest> requests) {
        if (requests.isEmpty()) {
            return;
        }
        for (NoteSoundLedger.StopRequest request : requests) {
            Player player = Bukkit.getPlayer(request.listener());
            if (player != null && player.getWorld().equals(world)) {
                player.stopSound(request.sound(), SoundCategory.RECORDS);
            }
        }
    }
}
