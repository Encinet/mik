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
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final class ImmediateScheduler implements NbsPlaybackEngine.Scheduler {
        private final AtomicInteger mainThreadTasks = new AtomicInteger();
        private final AtomicInteger repeatingTasks = new AtomicInteger();

        @Override
        public void runOnMainThread(Runnable task) {
            mainThreadTasks.incrementAndGet();
            task.run();
        }

        @Override
        public NbsPlaybackEngine.ScheduledPlayback scheduleEveryTick(Runnable task) {
            repeatingTasks.incrementAndGet();
            return () -> {};
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
