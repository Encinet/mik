package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsSong;

import java.util.ArrayList;
import java.util.List;

/** Advances an NBS song against monotonic time while preserving file tick timing. */
final class NbsPlaybackCursor {

    /** Retain at most one second or 20 song ticks of stale scheduler timing. */
    private static final double MAX_CATCH_UP_SECONDS = 1.0;
    private static final double MAX_CATCH_UP_TICKS = 20.0;
    private static final double SERVER_TICKS_PER_SECOND = 20.0;

    private final NbsSong song;
    private int currentTick;
    private int noteIndex;
    private int completedLoops;
    private double pendingTicks;
    private long lastPollNanos;
    private boolean started;
    private boolean finished;

    NbsPlaybackCursor(NbsSong song) {
        this.song = song;
    }

    PollResult poll(long nowNanos) {
        if (finished) {
            return new PollResult(List.of(), true);
        }
        List<NbsNote> notes = new ArrayList<>();
        if (!started) {
            started = true;
            lastPollNanos = nowNanos;
            appendCurrentTick(notes);
            return new PollResult(notes, false);
        }

        long elapsed = nowNanos - lastPollNanos;
        if (elapsed < 0) {
            lastPollNanos = nowNanos;
            elapsed = 0;
        }
        lastPollNanos = nowNanos;
        double elapsedTicks = elapsed * song.ticksPerSecond() / 1_000_000_000.0;
        double staleTimingLimit = Math.max(1.0, Math.min(MAX_CATCH_UP_TICKS,
                song.ticksPerSecond() * MAX_CATCH_UP_SECONDS));
        double normalPollLimit = Math.ceil(song.ticksPerSecond() / SERVER_TICKS_PER_SECOND);
        double catchUpLimit = Math.max(staleTimingLimit, normalPollLimit);
        pendingTicks = Math.min(catchUpLimit, pendingTicks + elapsedTicks);
        while (pendingTicks >= 1.0 && !finished) {
            pendingTicks -= 1.0;
            advanceTick();
            if (!finished) {
                appendCurrentTick(notes);
            }
        }
        return new PollResult(List.copyOf(notes), finished);
    }

    private void advanceTick() {
        currentTick++;
        if (currentTick < song.lengthTicks()) {
            return;
        }
        boolean canLoop = song.loopEnabled()
                && (song.maxLoopCount() == 0 || completedLoops < song.maxLoopCount());
        if (!canLoop) {
            finished = true;
            return;
        }
        completedLoops++;
        currentTick = song.loopStartTick();
        noteIndex = lowerBound(currentTick);
    }

    private void appendCurrentTick(List<NbsNote> output) {
        while (noteIndex < song.notes().size() && song.notes().get(noteIndex).tick() < currentTick) {
            noteIndex++;
        }
        while (noteIndex < song.notes().size() && song.notes().get(noteIndex).tick() == currentTick) {
            output.add(song.notes().get(noteIndex++));
        }
    }

    private int lowerBound(int tick) {
        int low = 0;
        int high = song.notes().size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (song.notes().get(middle).tick() < tick) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    record PollResult(List<NbsNote> notes, boolean finished) {
    }
}
