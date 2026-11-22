package org.encinet.mik.feature.entertainment.music.jukebox;

import org.bukkit.Location;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSoundSettings;
import org.encinet.mik.feature.entertainment.music.NoteScores;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia.MediaLease;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import org.encinet.mik.protocol.music.MusicMediaDescriptor;
import org.encinet.mik.protocol.music.MusicRepresentation;
import org.encinet.mik.protocol.music.NoteScore;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.NoteRhythmExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

class NotePlaybackEngineTest {

    private NotePlaybackEngine engine;

    @AfterEach
    void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void malformedScoreFailsBeforeNoteOutputIsScheduled() throws Exception {
        var scheduler = new ImmediateScheduler();
        engine = new NotePlaybackEngine(scheduler, java.util.concurrent.Executors.newSingleThreadExecutor(),
                NoteRhythmExtractor.INSTANCE, System::nanoTime, _ -> true);
        var callbacks = new RecordingCallbacks();
        var lease = scoreLease(NoteScores.evenlySpaced(100_000, 1).setDurationUs(0).build());
        engine.create(new Location(null, 0, 0, 0), lease, JukeboxSoundSettings.defaults(), callbacks,
                new RhythmTimeline("broken"), new JukeboxPlaybackGroup()).start();
        assertTrue(callbacks.terminal.await(5, TimeUnit.SECONDS));
        assertInstanceOf(java.io.IOException.class, callbacks.failure.get());
        assertEquals(0, scheduler.repeatingTasks.get());
        assertEquals(PlaybackStatus.STOPPED, callbacks.status.get());
    }

    @Test
    void stoppingBeforeParsingCompletesCannotSchedulePlayback() {
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        engine = new NotePlaybackEngine(scheduler, executor, NoteRhythmExtractor.INSTANCE, System::nanoTime, _ -> true);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        PlaybackSession session = engine.create(new Location(null, 0, 0, 0),
                minimalScore(),
                JukeboxSoundSettings.defaults(), callbacks, new RhythmTimeline("playback"), new JukeboxPlaybackGroup());

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
    void preparationDoesNotStartNotesOrSongClock() {
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NotePlaybackEngine(scheduler, executor, NoteRhythmExtractor.INSTANCE, clock::get, _ -> true);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        PlaybackSession session = engine.create(new Location(null, 0, 0, 0),
                minimalScore(),
                JukeboxSoundSettings.defaults(), callbacks, new RhythmTimeline("playback"), new JukeboxPlaybackGroup());

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
    void sessionPublishesTheBoundedMediaClockAfterAServerStall() {
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NotePlaybackEngine(scheduler, executor, NoteRhythmExtractor.INSTANCE, clock::get, _ -> true);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        PlaybackSession session = engine.create(new Location(null, 0, 0, 0),
                minimalScore(),
                JukeboxSoundSettings.defaults(), callbacks, new RhythmTimeline("playback"), new JukeboxPlaybackGroup());

        session.start();
        executor.runAll();
        assertEquals(0L, session.positionMillis());
        assertEquals(1, callbacks.started.get());

        clock.addAndGet(60_000_000_000L);
        scheduler.runPlaybackTick();

        assertEquals(100L, session.positionMillis(),
                "a 60-second wall-clock stall must not move past the media end");
        assertEquals(1, callbacks.finished.get());
    }

    @Test
    void sessionJoinsTheSharedJukeboxSongPosition() {
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NotePlaybackEngine(scheduler, executor, NoteRhythmExtractor.INSTANCE, clock::get, _ -> true);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        PlaybackSession session = engine.create(new Location(null, 0, 0, 0),
                minimalScore(),
                JukeboxSoundSettings.defaults(), callbacks,
                new RhythmTimeline("sync"),
                playbackGroupAt(50L));

        session.start();
        executor.runAll();

        assertEquals(PlaybackStatus.PLAYING, session.status());
        assertEquals(50L, session.positionMillis());
        assertEquals(1, callbacks.started.get());
    }

    @Test
    void aNewRhythmAttemptReplaysThePreparedChartFromTheBeginning() {
        ManualExecutor executor = new ManualExecutor();
        ImmediateScheduler scheduler = new ImmediateScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NotePlaybackEngine(scheduler, executor, NoteRhythmExtractor.INSTANCE, clock::get, _ -> true);
        MediaLease target = minimalScore();
        var timeline = new RhythmTimeline(
                "rhythm-replay");
        PlaybackAttempt first = new PlaybackAttempt(new JukeboxPlaybackGroup(clock::get));
        first.prepare(() -> engine.create(new Location(null, 0, 0, 0), target,
                JukeboxSoundSettings.defaults(), first.guard(new RecordingCallbacks()),
                timeline, first.group()));
        first.start();
        executor.runAll();
        clock.addAndGet(50_000_000L);
        scheduler.runPlaybackTick();
        assertEquals(50L, first.session().positionMillis());
        first.close();

        PlaybackAttempt second = new PlaybackAttempt(new JukeboxPlaybackGroup(clock::get));
        second.prepare(() -> engine.create(new Location(null, 0, 0, 0), target,
                JukeboxSoundSettings.defaults(), second.guard(new RecordingCallbacks()),
                timeline, second.group()));
        second.start();
        executor.runAll();

        assertTrue(timeline.complete());
        assertEquals(PlaybackStatus.PLAYING, second.session().status());
        assertEquals(0L, second.session().positionMillis());
        second.close();
    }

    @Test
    void synchronizedJukeboxesShareOneClockAndDropOutputsIndependently()
            {
        ManualExecutor executor = new ManualExecutor();
        MultiSessionScheduler scheduler = new MultiSessionScheduler();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        engine = new NotePlaybackEngine(scheduler, executor, NoteRhythmExtractor.INSTANCE, clock::get, _ -> true);
        MediaLease target = minimalScore();
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup(clock::get);
        RecordingCallbacks firstCallbacks = new RecordingCallbacks();
        RecordingCallbacks secondCallbacks = new RecordingCallbacks();
        PlaybackSession first = engine.create(new Location(null, 0, 0, 0), target,
                JukeboxSoundSettings.defaults(), firstCallbacks,
                new RhythmTimeline("first"),
                group);
        PlaybackSession second = engine.create(new Location(null, 16, 0, 0), target,
                JukeboxSoundSettings.defaults(), secondCallbacks,
                new RhythmTimeline("second"),
                group);

        first.start();
        second.start();
        executor.runAll();

        assertEquals(PlaybackStatus.PLAYING, first.status());
        assertEquals(PlaybackStatus.PLAYING, second.status());
        assertEquals(1, firstCallbacks.started.get());
        assertEquals(1, secondCallbacks.started.get());
        assertEquals(1, scheduler.activeTasks());
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
        group.seekTo(positionMillis);
        return group;
    }

    private static final class ImmediateScheduler implements NotePlaybackEngine.Scheduler {
        private final AtomicInteger mainThreadTasks = new AtomicInteger();
        private final AtomicInteger repeatingTasks = new AtomicInteger();
        private final AtomicReference<Runnable> repeatingTask = new AtomicReference<>();

        @Override
        public void runOnMainThread(Runnable task) {
            mainThreadTasks.incrementAndGet();
            task.run();
        }

        @Override
        public NotePlaybackEngine.ScheduledPlayback scheduleEveryTick(Runnable task) {
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

    private static final class MultiSessionScheduler implements NotePlaybackEngine.Scheduler {
        private final List<Runnable> repeatingTasks = new ArrayList<>();

        @Override
        public void runOnMainThread(Runnable task) {
            task.run();
        }

        @Override
        public NotePlaybackEngine.ScheduledPlayback scheduleEveryTick(Runnable task) {
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

    private static MediaLease minimalScore() {
        return scoreLease(NoteScores.evenlySpaced(100_000, 1).build());
    }

    private static MediaLease scoreLease(NoteScore score) {
        byte[] bytes = score.toByteArray();
        return AudioTrackLoaderStreamingTest.lease(bytes, "test-score", MusicMediaDescriptor.newBuilder()
                .setRepresentation(MusicRepresentation.NOTE_EVENTS).setRepresentationVersion(1)
                .setByteLength(bytes.length).setSeekable(true).build());
    }
}
