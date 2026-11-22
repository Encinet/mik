package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.ArrayList;
import java.util.List;
import org.encinet.mik.protocol.music.NoteEvent;
import org.encinet.mik.protocol.music.NoteScore;

/** Advances the media clock; loops return to their exact media-time boundary. */
final class NotePlaybackCursor {
    private static final long CATCH_UP_US = 250_000;
    private static final int MAX_BURST = 4096;
    private final NoteScore score;
    private long position;
    private long lastNanos;
    private long remainderNanos;
    private long loops;
    private int index;
    private boolean started;
    private boolean finished;

    NotePlaybackCursor(NoteScore score, long seekMs) {
        this.score = score;
        position = Math.clamp(seekMs, 0, score.getDurationUs() / 1000) * 1000;
        index = lowerBound(position);
    }

    void resume(long now) { if (started) lastNanos = now; }

    PollResult poll(long now) {
        long nanos = (started ? Math.max(0, now - lastNanos) : 0) + remainderNanos;
        started = true;
        lastNanos = now;
        remainderNanos = nanos % 1000;
        long delta = nanos / 1000;
        boolean reset = delta > CATCH_UP_US;
        if (reset) { skip(delta - CATCH_UP_US); delta = CATCH_UP_US; }
        var events = new ArrayList<NoteEvent>();
        int wraps = 0;
        while (!finished) {
            long remaining = score.getDurationUs() - position;
            long stop = position + Math.min(remaining, delta);
            while (index < score.getEventsCount() && score.getEvents(index).getTimeUs() <= stop) {
                var event = score.getEvents(index++);
                if (events.size() < MAX_BURST) events.add(event);
            }
            position = stop;
            if (delta < remaining) break;
            if (!wrap()) break;
            delta -= remaining;
            if (++wraps == 256) { skip(delta); reset = true; events.clear(); break; }
        }
        return new PollResult(List.copyOf(events), reset, finished, position / 1000);
    }

    private boolean wrap() {
        if (score.hasLoop() && (!score.getLoop().hasRepeats() || loops < score.getLoop().getRepeats())) {
            loops++;
            position = score.getLoop().getStartUs();
            index = lowerBound(position);
            return true;
        }
        finished = true;
        return false;
    }

    private void skip(long delta) {
        long remaining = score.getDurationUs() - position;
        if (delta >= remaining) {
            delta -= remaining;
            position = score.getDurationUs();
            if (!wrap()) return;
            long cycle = score.getDurationUs() - position;
            long skipped = Math.min(delta / cycle,
                    score.getLoop().hasRepeats() ? score.getLoop().getRepeats() - loops : Long.MAX_VALUE);
            loops += skipped;
            delta -= skipped * cycle;
        }
        position += Math.min(delta, score.getDurationUs() - position);
        index = lowerBound(position);
    }

    private int lowerBound(long time) {
        int low = 0;
        int high = score.getEventsCount();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (score.getEvents(mid).getTimeUs() < time) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    record PollResult(List<NoteEvent> notes, boolean resetSounds, boolean finished, long positionMillis) { }
}
