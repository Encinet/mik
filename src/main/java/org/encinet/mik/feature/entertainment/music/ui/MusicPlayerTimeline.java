package org.encinet.mik.feature.entertainment.music.ui;

import java.util.Objects;
import java.util.UUID;
import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackSnapshot;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;

/** Per-workspace clock and short seek burst. No backend reads or layout refreshes happen in the paint supplier. */
final class MusicPlayerTimeline implements AutoCloseable {
    interface Owner {
        boolean current(UUID target);
        /** Schedules one two-tick burst and returns its cancellation action. */
        Runnable later(Runnable action);
        void seek(MusicPlaybackSnapshot expected, long position);
        void refresh();
    }
    private final Owner owner;
    private MusicPlaybackSnapshot sample;
    private MusicPlaybackSnapshot pendingIdentity;
    private Long pendingPosition;
    private Runnable cancelTask;
    private long generation;

    MusicPlayerTimeline(Owner owner) { this.owner = Objects.requireNonNull(owner); }
    void accept(MusicPlaybackSnapshot next) {
        if (next == null || sample == null || !sample.samePlayback(next) || !next.seekable() || sample.status() != next.status()) cancel();
        sample = next;
    }
    double position() { return pendingPosition != null ? pendingPosition : sample == null ? 0 : sample.positionAt(System.nanoTime()); }

    void seek(MusicPlaybackSnapshot expected, long millis) {
        if (!owner.current(expected.target())
                || sample == null || !expected.samePlayback(sample)) throw new MusicProblem(MusicProblem.Code.CONFLICT);
        if (!expected.seekable() || expected.durationMillis() <= 0) throw new MusicProblem(MusicProblem.Code.UNSUPPORTED);
        pendingIdentity = expected;
        pendingPosition = Math.clamp(millis, 0, Math.max(0, expected.durationMillis() - 1));
        if (cancelTask == null) {
            long ticket = ++generation;
            cancelTask = owner.later(() -> flush(ticket));
        }
    }
    private void flush(long ticket) {
        if (generation != ticket || pendingIdentity == null || pendingPosition == null) return;
        cancelTask = null;
        var expected = pendingIdentity;
        long position = pendingPosition;
        pendingIdentity = null; pendingPosition = null;
        if (!owner.current(expected.target())) return;
        try { owner.seek(expected, position); }
        finally { owner.refresh(); }
    }
    private void cancel() {
        generation++;
        if (cancelTask != null) cancelTask.run();
        cancelTask = null; pendingIdentity = null; pendingPosition = null;
    }
    @Override public void close() { cancel(); sample = null; }
}
