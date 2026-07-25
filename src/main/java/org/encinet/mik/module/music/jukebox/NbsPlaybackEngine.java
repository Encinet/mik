package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.catalog.nbs.NbsInstruments;
import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsParser;
import org.encinet.mik.module.music.catalog.nbs.NbsSong;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loads NBS files off-thread and plays their note-block sounds on the main thread. */
final class NbsPlaybackEngine implements AutoCloseable {

    private final Scheduler scheduler;
    private final LocalMediaPreparer mediaPreparer;
    private final NbsParser parser;
    private final ExecutorService parsingExecutor;
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    NbsPlaybackEngine(JavaPlugin plugin) {
        this(new BukkitScheduler(plugin), new LocalMediaPreparer(), new NbsParser(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    NbsPlaybackEngine(Scheduler scheduler, LocalMediaPreparer mediaPreparer,
                       NbsParser parser, ExecutorService parsingExecutor) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.mediaPreparer = Objects.requireNonNull(mediaPreparer, "mediaPreparer");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.parsingExecutor = Objects.requireNonNull(parsingExecutor, "parsingExecutor");
    }

    PlaybackSession create(Location location, TrackTarget.NbsFile target,
                           PlaybackCallbacks callbacks) {
        if (closed.get()) {
            throw new IllegalStateException("NBS playback backend is closed");
        }
        Session session = new Session(location.clone(), target, callbacks);
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
        parsingExecutor.shutdownNow();
    }

    private final class Session implements PlaybackSession {
        private final Location location;
        private final TrackTarget.NbsFile target;
        private final PlaybackCallbacks callbacks;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile PlaybackStatus status = PlaybackStatus.LOADING;
        private volatile CompletableFuture<NbsSong> parsing;
        private volatile NbsPlaybackCursor cursor;
        private volatile ScheduledPlayback task;

        private Session(Location location, TrackTarget.NbsFile target,
                        PlaybackCallbacks callbacks) {
            this.location = location;
            this.target = Objects.requireNonNull(target, "target");
            this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
        }

        @Override
        public void start() {
            if (!started.compareAndSet(false, true) || terminal.get()) {
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

        private NbsSong loadSong() {
            try {
                return parser.parse(mediaPreparer.prepare(target));
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
            try {
                cursor = new NbsPlaybackCursor(Objects.requireNonNull(song, "song"));
                task = scheduler.scheduleEveryTick(this::tick);
                status = PlaybackStatus.PLAYING;
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
                NbsPlaybackCursor.PollResult result = cursor.poll(System.nanoTime());
                playNotes(location, result.notes());
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
        public void stop() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            cleanup();
        }

        private void fail(Throwable error) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
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
            cleanup();
            callbacks.cancelled();
        }

        private void cleanup() {
            status = PlaybackStatus.STOPPED;
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

    private static void playNotes(Location jukeboxLocation, List<NbsNote> notes) {
        World world = jukeboxLocation.getWorld();
        if (world == null) {
            return;
        }
        for (NbsNote note : notes) {
            float volume = NbsPlaybackVolume.volume(note);
            if (volume == 0.0F) {
                continue;
            }
            Location soundLocation = jukeboxLocation.clone().add(
                    0.5 + note.panning() / 50.0, 1.0, 0.5);
            float semitones = note.key() - 45 + note.finePitch() / 100.0F;
            float pitch = (float) Math.pow(2.0, semitones / 12.0);
            pitch = Math.max(0.5F, Math.min(2.0F, pitch));
            world.playSound(soundLocation, NbsInstruments.minecraftSound(note.instrument()),
                    SoundCategory.RECORDS, volume, pitch);
        }
    }
}
