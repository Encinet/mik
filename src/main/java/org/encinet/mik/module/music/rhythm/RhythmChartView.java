package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.rhythm.analysis.RhythmBeat;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Per-player density projection of one shared extracted rhythm track.
 *
 * <p>Only pulses closer than the difficulty's minimum spacing compete, and the
 * stronger local accent wins after the full stability horizon is observed. This
 * avoids both transient bursts and the old near-double spacing gap. Extraction
 * remains mode-independent. The assigned input is the default lane for keyed
 * modes; spatial modes may instead consume cue identity, time, and strength.</p>
 */
public final class RhythmChartView {
    private static final long MAXIMUM_ONSET_JITTER_MILLIS = 25L;
    private static final long MINIMUM_ONSET_JITTER_MILLIS = 8L;

    private final RhythmTrack track;
    private final RhythmDifficulty difficulty;
    private final RhythmLaneSequencer lanes;
    private final List<RhythmCue> selected = new ArrayList<>();
    private long evaluationCursorMillis;
    private RhythmBeat pending;

    public RhythmChartView(RhythmTrack track, RhythmDifficulty difficulty) {
        this(track, difficulty, 0L);
    }

    /** Starts projection near a join position instead of scanning the elapsed song. */
    public RhythmChartView(RhythmTrack track, RhythmDifficulty difficulty,
                           long startAtMillis) {
        this.track = Objects.requireNonNull(track, "track");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.lanes = new RhythmLaneSequencer(track.seed());
        this.evaluationCursorMillis = Math.max(0L, startAtMillis);
    }

    public RhythmDifficulty difficulty() {
        return difficulty;
    }

    public List<RhythmCue> between(long fromMillis, long toMillis) {
        long from = Math.max(0L, fromMillis);
        long to = Math.max(from, toMillis);
        evaluateThrough(to);
        List<RhythmCue> result = new ArrayList<>();
        for (int index = lowerBound(from); index < selected.size(); index++) {
            RhythmCue cue = selected.get(index);
            if (cue.timeMillis() > to) break;
            result.add(cue);
        }
        return List.copyOf(result);
    }

    /** Whether the chart is finalized through the requested playback time. */
    public boolean preparedThrough(long timeMillis) {
        if (track.complete()) return true;
        long spacing = difficulty.minimumCueSpacingMillis();
        long required = saturatedAdd(Math.max(0L, timeMillis), spacing);
        return track.analyzedThroughMillis() >= required;
    }

    public boolean complete() {
        return track.complete();
    }

    /**
     * Releases projected cues that can no longer be queried by a moving game
     * session. Lane sequencing and the extraction cursor remain continuous.
     */
    void discardBefore(long timeMillis) {
        long cutoff = Math.max(0L, timeMillis);
        evaluateThrough(cutoff);
        int removalCount = lowerBound(cutoff);
        if (removalCount > 0) selected.subList(0, removalCount).clear();
    }

    int retainedCueCount() {
        return selected.size();
    }

    /** Watermark of stable, salience-selected cues rather than extractor frames. */
    public long analyzedThroughMillis() {
        if (track.complete()) return track.analyzedThroughMillis();
        return Math.max(0L, track.analyzedThroughMillis()
                - difficulty.minimumCueSpacingMillis());
    }

    private void evaluateThrough(long requestedMillis) {
        long spacing = difficulty.minimumCueSpacingMillis();
        long competitionSpacing = competitionSpacing(spacing);
        long rawAvailable = track.complete()
                ? saturatedAdd(requestedMillis, spacing)
                : track.analyzedThroughMillis();
        if (evaluationCursorMillis <= rawAvailable) {
            for (RhythmBeat beat : track.between(evaluationCursorMillis, rawAvailable)) {
                consider(beat, competitionSpacing);
            }
            evaluationCursorMillis = saturatedAdd(rawAvailable, 1L);
        }
        if (pending != null
                && saturatedAdd(pending.timeMillis(), spacing) <= rawAvailable) {
            commitPending();
        }
    }

    private void consider(RhythmBeat beat, long spacing) {
        if (pending == null) {
            pending = beat;
            return;
        }
        if (beat.timeMillis() - pending.timeMillis() < spacing) {
            if (stronger(beat, pending)) pending = beat;
            return;
        }
        commitPending();
        pending = beat;
    }

    private void commitPending() {
        RhythmBeat beat = pending;
        if (beat == null) return;
        selected.add(new RhythmCue(beat.id(), beat.timeMillis(),
                lanes.next(beat.signature(), beat.stereoBalance(),
                        beat.toneBalance()), beat.strength()));
        pending = null;
    }

    private static boolean stronger(RhythmBeat candidate, RhythmBeat current) {
        int strength = Double.compare(candidate.strength(), current.strength());
        if (strength != 0) return strength > 0;
        if (candidate.timeMillis() != current.timeMillis()) return false;
        return candidate.id() < current.id();
    }

    /**
     * Onsets are measured in 20 ms PCM windows, so a stable musical period can
     * alternate just below and above a difficulty boundary (for example
     * 240/260 ms around Normal's 250 ms spacing). Reserve a small, bounded
     * tolerance for that quantization instead of deleting every second beat.
     */
    private static long competitionSpacing(long nominalSpacingMillis) {
        long allowance = Math.clamp(nominalSpacingMillis / 10L,
                MINIMUM_ONSET_JITTER_MILLIS, MAXIMUM_ONSET_JITTER_MILLIS);
        return Math.max(1L, nominalSpacingMillis - allowance);
    }

    private int lowerBound(long timeMillis) {
        int low = 0;
        int high = selected.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (selected.get(middle).timeMillis() < timeMillis) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }
}
