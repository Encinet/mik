package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.shared.util.ShutdownSequence;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Owns one backend run, its clock, lyrics and callback lifetime.
 * A waiting rhythm disc creates a new attempt for every game; its chart can be reused separately.
 */
final class PlaybackAttempt implements AutoCloseable {
    private final UUID id = UUID.randomUUID();
    private final JukeboxPlaybackGroup group;
    private volatile PlaybackSession session;
    private volatile boolean closed;
    private boolean preparing;
    private boolean started;
    private boolean recorded;
    private final java.util.function.LongSupplier nanoClock;
    private long listenedNanos, countingSince = -1;
    synchronized void countingPaused(boolean paused) {
        long now = nanoClock.getAsLong();
        if (countingSince >= 0) listenedNanos += Math.max(0, now - countingSince);
        countingSince = paused || !started || closed ? -1 : now;
    }

    private Runnable stopLyrics;

    PlaybackAttempt() {
        this(new JukeboxPlaybackGroup(), System::nanoTime);
    }

    PlaybackAttempt(JukeboxPlaybackGroup group) {
        this(group, System::nanoTime);
    }
    PlaybackAttempt(JukeboxPlaybackGroup group, java.util.function.LongSupplier nanoClock) {
        this.group = Objects.requireNonNull(group, "group");
        this.nanoClock = nanoClock;
    }

    UUID id() {
        return id;
    }

    JukeboxPlaybackGroup group() {
        return group;
    }

    PlaybackSession session() {
        return session;
    }

    boolean active() {
        return !closed;
    }

    void prepare(Supplier<PlaybackSession> factory) {
        synchronized (this) {
            if (closed || preparing) return;
            preparing = true;
        }
        PlaybackSession created = Objects.requireNonNull(factory.get(), "session");
        boolean accepted;
        synchronized (this) {
            accepted = !closed;
            if (accepted) session = created;
        }
        if (accepted) {
            created.prepare();
        } else {
            created.stop();
        }
    }

    void start() {
        PlaybackSession current = session;
        if (!closed && current != null) current.start();
    }

    synchronized boolean claimStarted() {
        if (closed || started) return false;
        started = true;
        countingSince = group.paused() ? -1 : nanoClock.getAsLong();
        return true;
    }

    synchronized boolean claimPlaybackRecord(boolean finishedNaturally, Duration duration) {
        if (!started || recorded) return false;
        long position = (listenedNanos + (countingSince < 0 ? 0 : Math.max(0, nanoClock.getAsLong()-countingSince))) / 1_000_000L;
        if (!PlaybackCountPolicy.qualifies(finishedNaturally, position, duration)) return false;
        recorded = true;
        return group.claimPlaybackStatistic();
    }

    /** Immediately releases a lyric display delivered after stop or a duplicate start callback. */
    void attachLyrics(Runnable release) {
        Objects.requireNonNull(release, "release");
        boolean accepted;
        synchronized (this) {
            accepted = !closed && stopLyrics == null;
            if (accepted) stopLyrics = release;
        }
        if (!accepted) release.run();
    }

    /** Backend events from a retired run must never mutate the disc's next run. */
    PlaybackCallbacks guard(PlaybackCallbacks delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return new PlaybackCallbacks() {
            @Override
            public boolean isValid() {
                return !closed && delegate.isValid();
            }

            @Override
            public void started() {
                if (!closed) delegate.started();
            }

            @Override
            public void retrying() {
                if (!closed) delegate.retrying();
            }

            @Override
            public void failed(Throwable error) {
                if (!closed) delegate.failed(error);
            }

            @Override
            public void finished() {
                if (!closed) delegate.finished();
            }

            @Override
            public void cancelled() {
                if (!closed) delegate.cancelled();
            }
        };
    }

    @Override
    public void close() {
        PlaybackSession current;
        Runnable lyrics;
        synchronized (this) {
            if (closed) return;
            countingPaused(true);
            closed = true;
            current = session;
            lyrics = stopLyrics;
            stopLyrics = null;
        }
        ShutdownSequence shutdown = new ShutdownSequence();
        if (lyrics != null) shutdown.attempt("lyrics", lyrics::run);
        if (current != null) shutdown.attempt("backend session", current::stop);
        shutdown.finish("playback attempt");
    }
}
