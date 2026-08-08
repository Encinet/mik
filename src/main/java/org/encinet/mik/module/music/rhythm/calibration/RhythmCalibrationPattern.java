package org.encinet.mik.module.music.rhythm.calibration;

import java.util.ArrayList;
import java.util.List;

/** A constant rhythm phrase shared by all latency calibration stages. */
public final class RhythmCalibrationPattern {
    static final long BEAT_INTERVAL_MILLIS = 800L;
    static final int PHRASE_BEATS = 8;
    private static final int BAR_BEATS = 4;

    /*
     * Two bars of 4/4 at 75 BPM. A fixed quarter-note pulse makes every cue
     * predictable and prevents rhythmic variation from affecting measurements.
     */

    private final List<Long> cueTimesMillis;
    private final long durationMillis;

    private RhythmCalibrationPattern() {
        List<Long> cues = new ArrayList<>(PHRASE_BEATS);
        for (int beat = 0; beat < PHRASE_BEATS; beat++) {
            cues.add(beat * BEAT_INTERVAL_MILLIS);
        }
        this.cueTimesMillis = List.copyOf(cues);
        this.durationMillis = PHRASE_BEATS * BEAT_INTERVAL_MILLIS;
        validateIntervals();
    }

    public static RhythmCalibrationPattern fixed() {
        return new RhythmCalibrationPattern();
    }

    public List<Long> cueTimesMillis() {
        return cueTimesMillis;
    }

    public int cueCount() {
        return cueTimesMillis.size();
    }

    public long durationMillis() {
        return durationMillis;
    }

    /** Resolves a cue on the infinitely repeating phrase timeline. */
    public long cueTimeMillis(long cueIndex) {
        long safeIndex = Math.max(0L, cueIndex);
        long cycle = safeIndex / cueCount();
        long offset = cueTimesMillis.get((int) (safeIndex % cueCount()));
        if (cycle > (Long.MAX_VALUE - offset) / durationMillis) {
            return Long.MAX_VALUE;
        }
        return cycle * durationMillis + offset;
    }

    public long cueIndexNearest(long positionMillis) {
        long after = cueIndexAtOrAfter(positionMillis);
        if (after == 0L) return 0L;
        long previousTime = cueTimeMillis(after - 1L);
        long nextTime = cueTimeMillis(after);
        long boundedPosition = Math.max(0L, positionMillis);
        return boundedPosition - previousTime <= nextTime - boundedPosition
                ? after - 1L : after;
    }

    public long cueIndexAtOrAfter(long positionMillis) {
        if (positionMillis <= 0L) return 0L;
        long cycle = positionMillis / durationMillis;
        long withinCycle = positionMillis % durationMillis;
        int low = 0;
        int high = cueCount();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cueTimesMillis.get(middle) < withinCycle) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        long indexInCycle = low == cueCount() ? 0L : low;
        long targetCycle = low == cueCount() ? cycle + 1L : cycle;
        if (targetCycle > (Long.MAX_VALUE - indexInCycle) / cueCount()) {
            return Long.MAX_VALUE;
        }
        return targetCycle * cueCount() + indexInCycle;
    }

    public double strength(long cueIndex) {
        int phraseIndex = (int) (Math.max(0L, cueIndex) % cueCount());
        long beat = cueTimesMillis.get(phraseIndex) / BEAT_INTERVAL_MILLIS;
        if (beat % BAR_BEATS == 0L) return 1.0;
        if (beat % BAR_BEATS == 2L) return 0.92;
        return 0.78;
    }

    private void validateIntervals() {
        if (cueTimesMillis.isEmpty() || cueTimesMillis.getFirst() != 0L) {
            throw new IllegalArgumentException(
                    "calibration groove must start on the downbeat");
        }
        for (int index = 1; index <= cueTimesMillis.size(); index++) {
            long previous = cueTimesMillis.get(index - 1);
            long next = index == cueTimesMillis.size()
                    ? durationMillis : cueTimesMillis.get(index);
            if (next - previous != BEAT_INTERVAL_MILLIS) {
                throw new IllegalArgumentException(
                        "calibration cues must use a constant interval");
            }
        }
        if (!cueTimesMillis.contains(
                BAR_BEATS * BEAT_INTERVAL_MILLIS)) {
            throw new IllegalArgumentException(
                    "calibration rhythm must anchor both bars");
        }
    }
}
