package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsNoteType;
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
    private double ticksPerSecond;
    private double pendingSeconds;
    private double completedTickSeconds;
    private long lastPollNanos;
    private boolean started;
    private boolean finished;

    NbsPlaybackCursor(NbsSong song) {
        this.song = song;
        this.ticksPerSecond = song.ticksPerSecond();
    }

    PollResult poll(long nowNanos) {
        if (finished) {
            return result(List.of());
        }
        List<NbsNote> notes = new ArrayList<>();
        if (!started) {
            started = true;
            lastPollNanos = nowNanos;
            appendCurrentTick(notes);
            return result(notes);
        }

        long elapsed = nowNanos - lastPollNanos;
        if (elapsed < 0) {
            lastPollNanos = nowNanos;
            elapsed = 0;
        }
        lastPollNanos = nowNanos;
        double elapsedSeconds = elapsed / 1_000_000_000.0;
        double staleTimingLimit = Math.max(1.0 / ticksPerSecond,
                Math.min(MAX_CATCH_UP_SECONDS, MAX_CATCH_UP_TICKS / ticksPerSecond));
        double normalPollLimit = Math.ceil(ticksPerSecond / SERVER_TICKS_PER_SECOND)
                / ticksPerSecond;
        double catchUpLimit = Math.max(staleTimingLimit, normalPollLimit);
        pendingSeconds = Math.min(catchUpLimit, pendingSeconds + elapsedSeconds);
        double tickSeconds = 1.0 / ticksPerSecond;
        while (pendingSeconds + 1.0e-12 >= tickSeconds && !finished) {
            pendingSeconds -= tickSeconds;
            completedTickSeconds += tickSeconds;
            advanceTick();
            if (!finished) {
                appendCurrentTick(notes);
                tickSeconds = 1.0 / ticksPerSecond;
            }
        }
        return result(notes);
    }

    /**
     * Playback time owned by the same bounded clock that emits notes.
     *
     * <p>When the server stalls, stale scheduler debt is deliberately dropped.
     * Reporting wall time here would therefore put rhythm visuals permanently
     * ahead of the NBS sounds. Completed songs also exclude any elapsed time that
     * arrived after their final tick.</p>
     */
    private PollResult result(List<NbsNote> notes) {
        double positionSeconds = completedTickSeconds
                + (finished ? 0.0 : pendingSeconds);
        long positionMillis = Math.max(0L, Math.round(positionSeconds * 1_000.0));
        return new PollResult(notes, finished, positionMillis);
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
        ticksPerSecond = song.ticksPerSecondBefore(currentTick);
    }

    private void appendCurrentTick(List<NbsNote> output) {
        while (noteIndex < song.notes().size() && song.notes().get(noteIndex).tick() < currentTick) {
            noteIndex++;
        }
        while (noteIndex < song.notes().size() && song.notes().get(noteIndex).tick() == currentTick) {
            NbsNote note = song.notes().get(noteIndex++);
            output.add(note);
            if (note.type() == NbsNoteType.TEMPO_CHANGE
                    && NbsSong.isPlayableTempo(note.tempoChangeTicksPerSecond())) {
                ticksPerSecond = note.tempoChangeTicksPerSecond();
            }
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

    record PollResult(List<NbsNote> notes, boolean finished, long positionMillis) {
        PollResult {
            notes = List.copyOf(notes);
            positionMillis = Math.max(0L, positionMillis);
        }
    }
}
