package org.encinet.mik.module.music.rhythm;

import java.util.Objects;

/** Immutable end-of-song snapshot that remains valid after playback cleanup. */
public record RhythmGameResult(long score, long maximumCombo,
                               long perfectHits, long greatHits,
                               long goodHits, long misses,
                               long timingErrorTotalMillis) {
    public RhythmGameResult {
        if (score < 0L || maximumCombo < 0L || perfectHits < 0L
                || greatHits < 0L || goodHits < 0L || misses < 0L) {
            throw new IllegalArgumentException("result counters must not be negative");
        }
    }

    public static RhythmGameResult from(RhythmGameSession.View view) {
        Objects.requireNonNull(view, "view");
        return new RhythmGameResult(view.score(), view.maximumCombo(),
                view.perfectHits(), view.greatHits(), view.goodHits(),
                view.misses(), view.timingErrorTotalMillis());
    }

    public long hits() {
        return saturatedAdd(saturatedAdd(perfectHits, greatHits), goodHits);
    }

    public long totalJudgements() {
        return saturatedAdd(hits(), misses);
    }

    /** Weighted accuracy: Perfect 100%, Great 75%, Good 45%, Miss 0%. */
    public double accuracy() {
        long total = totalJudgements();
        if (total == 0L) return 1.0;
        return (perfectHits + greatHits * 0.75 + goodHits * 0.45) / total;
    }

    /** Negative is early and positive is late. */
    public double meanTimingErrorMillis() {
        long hits = hits();
        return hits == 0L ? 0.0 : timingErrorTotalMillis / (double) hits;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
