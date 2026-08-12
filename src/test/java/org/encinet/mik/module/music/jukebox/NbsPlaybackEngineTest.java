package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.catalog.nbs.NbsParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbsPlaybackEngineTest {

    @TempDir
    Path directory;

    private NbsPlaybackEngine engine;

    @AfterEach
    void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void deletedFileFailsDuringPlaybackPreparation() throws Exception {
        Path file = directory.resolve("deleted.nbs");
        Files.write(file, minimalNbs());
        TrackTarget.NbsFile target = new TrackTarget.NbsFile(file, directory);
        Files.delete(file);
        ImmediateScheduler scheduler = new ImmediateScheduler();
        engine = new NbsPlaybackEngine(scheduler, new LocalMediaPreparer(), new NbsParser(),
                java.util.concurrent.Executors.newSingleThreadExecutor());
        RecordingCallbacks callbacks = new RecordingCallbacks();

        engine.create(new Location(null, 0, 0, 0), target,
                JukeboxSoundSettings.defaults(), callbacks).start();

        assertTrue(callbacks.terminal.await(5, TimeUnit.SECONDS));
        assertInstanceOf(java.io.IOException.class, callbacks.failure.get());
        assertEquals(0, scheduler.repeatingTasks.get());
        assertEquals(PlaybackStatus.STOPPED, callbacks.status.get());
    }

    @Test
    void stoppingBeforeParsingCompletesCannotSchedulePlayback() throws Exception {
        Path file = directory.resolve("song.nbs");
        Files.write(file, minimalNbs());
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        engine = new NbsPlaybackEngine(scheduler, new LocalMediaPreparer(), new NbsParser(),
                executor);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        PlaybackSession session = engine.create(new Location(null, 0, 0, 0),
                new TrackTarget.NbsFile(file, directory),
                JukeboxSoundSettings.defaults(), callbacks);

        session.start();
        session.stop();
        executor.runAll();

        assertEquals(0, scheduler.mainThreadTasks.get());
        assertEquals(0, scheduler.repeatingTasks.get());
        assertEquals(0, callbacks.started.get());
        assertEquals(0, callbacks.failed.get());
        assertEquals(0, callbacks.finished.get());
        assertEquals(0, callbacks.cancelled.get());
        assertEquals(PlaybackStatus.STOPPED, session.status());
    }

    @Test
    void preparationDoesNotStartNotesOrSongClock() throws Exception {
        Path file = directory.resolve("prepared.nbs");
        Files.write(file, minimalNbs());
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NbsPlaybackEngine(scheduler, new LocalMediaPreparer(),
                new NbsParser(), executor,
                org.encinet.mik.module.music.rhythm.analysis.NbsRhythmExtractor.INSTANCE,
                clock::get);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        PlaybackSession session = engine.create(new Location(null, 0, 0, 0),
                new TrackTarget.NbsFile(file, directory),
                JukeboxSoundSettings.defaults(), callbacks);

        session.prepare();
        executor.runAll();
        clock.addAndGet(10_000_000_000L);

        assertEquals(PlaybackStatus.LOADING, session.status());
        assertEquals(0L, session.positionMillis());
        assertEquals(0, callbacks.started.get());
        assertEquals(0, scheduler.repeatingTasks.get());

        session.start();

        assertEquals(PlaybackStatus.PLAYING, session.status());
        assertEquals(0L, session.positionMillis(),
                "the playback clock must begin when the countdown releases it");
        assertEquals(1, callbacks.started.get());
        assertEquals(1, scheduler.repeatingTasks.get());
    }

    @Test
    void sessionPublishesTheBoundedNbsClockAfterAServerStall() throws Exception {
        Path file = directory.resolve("clock.nbs");
        Files.write(file, minimalNbs());
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NbsPlaybackEngine(scheduler, new LocalMediaPreparer(),
                new NbsParser(), executor,
                org.encinet.mik.module.music.rhythm.analysis.NbsRhythmExtractor.INSTANCE,
                clock::get);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        PlaybackSession session = engine.create(new Location(null, 0, 0, 0),
                new TrackTarget.NbsFile(file, directory),
                JukeboxSoundSettings.defaults(), callbacks);

        session.start();
        executor.runAll();
        assertEquals(0L, session.positionMillis());
        assertEquals(1, callbacks.started.get());

        clock.addAndGet(60_000_000_000L);
        scheduler.runPlaybackTick();

        assertEquals(100L, session.positionMillis(),
                "a 60-second wall-clock stall must not move past the final NBS tick");
        assertEquals(1, callbacks.finished.get());
    }

    @Test
    void sessionJoinsTheSharedJukeboxSongPosition() throws Exception {
        Path file = directory.resolve("synchronized.nbs");
        Files.write(file, minimalNbs());
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NbsPlaybackEngine(scheduler, new LocalMediaPreparer(),
                new NbsParser(), executor,
                org.encinet.mik.module.music.rhythm.analysis.NbsRhythmExtractor.INSTANCE,
                clock::get);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        PlaybackSession session = engine.create(new Location(null, 0, 0, 0),
                new TrackTarget.NbsFile(file, directory),
                JukeboxSoundSettings.defaults(), callbacks,
                new org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline("sync"),
                playbackGroupAt(50L));

        session.start();
        executor.runAll();

        assertEquals(PlaybackStatus.PLAYING, session.status());
        assertEquals(50L, session.positionMillis());
        assertEquals(1, callbacks.started.get());
    }

    @Test
    void synchronizedJukeboxesKeepIndependentSessionsPlayingAtTheSameTime()
            throws Exception {
        Path file = directory.resolve("simultaneous.nbs");
        Files.write(file, minimalNbs());
        ManualExecutor executor = new ManualExecutor();
        MultiSessionScheduler scheduler = new MultiSessionScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NbsPlaybackEngine(scheduler, new LocalMediaPreparer(),
                new NbsParser(), executor,
                org.encinet.mik.module.music.rhythm.analysis.NbsRhythmExtractor.INSTANCE,
                clock::get);
        TrackTarget.NbsFile target = new TrackTarget.NbsFile(file, directory);
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup(clock::get);
        RecordingCallbacks firstCallbacks = new RecordingCallbacks();
        RecordingCallbacks secondCallbacks = new RecordingCallbacks();
        PlaybackSession first = engine.create(new Location(null, 0, 0, 0), target,
                JukeboxSoundSettings.defaults(), firstCallbacks,
                new org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline("first"),
                group);
        PlaybackSession second = engine.create(new Location(null, 16, 0, 0), target,
                JukeboxSoundSettings.defaults(), secondCallbacks,
                new org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline("second"),
                group);

        first.start();
        second.start();
        executor.runAll();

        assertEquals(PlaybackStatus.PLAYING, first.status());
        assertEquals(PlaybackStatus.PLAYING, second.status());
        assertEquals(1, firstCallbacks.started.get());
        assertEquals(1, secondCallbacks.started.get());
        assertEquals(2, scheduler.activeTasks());
        assertTrue(group.claimStartedAnnouncement());
        assertFalse(group.claimStartedAnnouncement(),
                "two playing sessions must still emit only one group announcement");

        first.stop();

        assertEquals(PlaybackStatus.STOPPED, first.status());
        assertEquals(PlaybackStatus.PLAYING, second.status(),
                "stopping one synchronized jukebox must not stop the other");
        assertEquals(1, scheduler.activeTasks());
    }

    private static JukeboxPlaybackGroup playbackGroupAt(long positionMillis) {
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup();
        group.publishPositionMillis(positionMillis);
        return group;
    }

    private static final class ImmediateScheduler implements NbsPlaybackEngine.Scheduler {
        private final AtomicInteger mainThreadTasks = new AtomicInteger();
        private final AtomicInteger repeatingTasks = new AtomicInteger();
        private final AtomicReference<Runnable> repeatingTask = new AtomicReference<>();

        @Override
        public void runOnMainThread(Runnable task) {
            mainThreadTasks.incrementAndGet();
            task.run();
        }

        @Override
        public NbsPlaybackEngine.ScheduledPlayback scheduleEveryTick(Runnable task) {
            repeatingTasks.incrementAndGet();
            repeatingTask.set(task);
            return () -> repeatingTask.compareAndSet(task, null);
        }

        private void runPlaybackTick() {
            Runnable task = repeatingTask.get();
            if (task == null) throw new IllegalStateException("no playback task scheduled");
            task.run();
        }
    }

    private static final class MultiSessionScheduler implements NbsPlaybackEngine.Scheduler {
        private final List<Runnable> repeatingTasks = new ArrayList<>();

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public NbsPlaybackEngine.ScheduledPlayback scheduleEveryTick(Runnable task) {
            repeatingTasks.add(task);
            return () -> repeatingTasks.remove(task);
        }

        private int activeTasks() {
            return repeatingTasks.size();
        }
    }

    private static final class RecordingCallbacks implements PlaybackCallbacks {
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final AtomicInteger finished = new AtomicInteger();
        private final AtomicInteger cancelled = new AtomicInteger();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<PlaybackStatus> status =
                new AtomicReference<>(PlaybackStatus.LOADING);

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void started() {
            started.incrementAndGet();
            status.set(PlaybackStatus.PLAYING);
        }

        @Override
        public void failed(Throwable error) {
            failure.set(error);
            failed.incrementAndGet();
            status.set(PlaybackStatus.STOPPED);
            terminal.countDown();
        }

        @Override
        public void finished() {
            finished.incrementAndGet();
            status.set(PlaybackStatus.STOPPED);
            terminal.countDown();
        }

        @Override
        public void cancelled() {
            cancelled.incrementAndGet();
            status.set(PlaybackStatus.STOPPED);
            terminal.countDown();
        }
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            java.util.List<Runnable> pending = java.util.List.copyOf(tasks);
            tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }

    private static byte[] minimalNbs() {
        Bytes bytes = new Bytes();
        bytes.u16(0).u8(5).u8(20).u16(1).u16(1)
                .string("Song").string("Composer").string("").string("")
                .u16(1000).u8(0).u8(10).u8(4)
                .i32(0).i32(0).i32(0).i32(0).i32(0).string("")
                .u8(0).u8(0).u16(0)
                .u16(1).u16(1).u8(0).u8(45).u8(100).u8(100).u16(0)
                .u16(0).u16(0)
                .string("Layer").u8(0).u8(100).u8(100).u8(0);
        return bytes.output.toByteArray();
    }

    private static final class Bytes {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private Bytes u8(int value) {
            output.write(value & 0xff);
            return this;
        }

        private Bytes u16(int value) {
            return u8(value).u8(value >>> 8);
        }

        private Bytes i32(int value) {
            return u8(value).u8(value >>> 8).u8(value >>> 16).u8(value >>> 24);
        }

        private Bytes string(String value) {
            byte[] data = value.getBytes(StandardCharsets.UTF_8);
            i32(data.length);
            output.writeBytes(data);
            return this;
        }
    }
}
