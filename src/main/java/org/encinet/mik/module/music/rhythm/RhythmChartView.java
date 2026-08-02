package org.encinet.mik.module.music.rhythm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-player salience projection of one shared raw timeline.
 *
 * <p>Each selection window is observed in full before its strongest onset is
 * committed. This produces a stable, density-bounded chart and prevents an
 * earlier weak transient from hiding a stronger musical accent.</p>
 */
public final class RhythmChartView {
    private static final RhythmInput[] DIRECTIONS = {
            RhythmInput.FORWARD, RhythmInput.BACKWARD,
            RhythmInput.LEFT, RhythmInput.RIGHT
    };

    private final RhythmTimeline timeline;
    private final RhythmDifficulty difficulty;
    private final List<RhythmCue> selected = new ArrayList<>();
    private long selectionCursorMillis;
    private RhythmInput previousInput;
    private int cuesSinceVertical = 12;

    public RhythmChartView(RhythmTimeline timeline, RhythmDifficulty difficulty) {
        this(timeline, difficulty, 0L);
    }

    /** Starts projection near a join position instead of scanning the elapsed song. */
    public RhythmChartView(RhythmTimeline timeline, RhythmDifficulty difficulty,
                           long startAtMillis) {
        this.timeline = Objects.requireNonNull(timeline, "timeline");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.selectionCursorMillis = Math.max(0L, startAtMillis);
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
        if (timeline.complete()) return true;
        long spacing = difficulty.minimumCueSpacingMillis();
        long required = saturatedAdd(Math.max(0L, timeMillis), spacing);
        return timeline.analyzedThroughMillis() >= required;
    }

    public boolean complete() {
        return timeline.complete();
    }

    /** Watermark of stable, salience-selected cues rather than raw analyzer frames. */
    public long analyzedThroughMillis() {
        if (timeline.complete()) return timeline.analyzedThroughMillis();
        return Math.max(0L, timeline.analyzedThroughMillis()
                - difficulty.minimumCueSpacingMillis());
    }

    private void evaluateThrough(long requestedMillis) {
        long spacing = difficulty.minimumCueSpacingMillis();
        long rawAvailable = timeline.complete()
                ? saturatedAdd(requestedMillis, spacing)
                : timeline.analyzedThroughMillis();
        while (selectionCursorMillis <= requestedMillis) {
            long windowEnd = saturatedAdd(selectionCursorMillis, spacing - 1L);
            if (windowEnd > rawAvailable) return;
            List<RhythmCue> window = timeline.between(selectionCursorMillis, windowEnd);
            if (window.isEmpty()) {
                selectionCursorMillis = saturatedAdd(windowEnd, 1L);
                continue;
            }
            RhythmCue strongest = window.stream()
                    .max(Comparator.comparingDouble(RhythmCue::strength)
                            .thenComparing(Comparator.comparingLong(
                                    RhythmCue::timeMillis).reversed())
                            .thenComparing(Comparator.comparingLong(
                                    RhythmCue::id).reversed()))
                    .orElseThrow();
            RhythmCue arranged = arrange(strongest);
            selected.add(arranged);
            selectionCursorMillis = saturatedAdd(
                    strongest.timeMillis(), spacing);
        }
    }

    private RhythmCue arrange(RhythmCue cue) {
        RhythmInput input = cue.input();
        int verticalGap = minimumVerticalGap();
        boolean vertical = isVertical(input);
        if (input == previousInput || vertical && cuesSinceVertical < verticalGap) {
            input = alternateDirection(cue);
            vertical = false;
        }
        if (vertical) cuesSinceVertical = 0;
        else cuesSinceVertical++;
        previousInput = input;
        return input == cue.input() ? cue
                : new RhythmCue(cue.id(), cue.timeMillis(), input, cue.strength());
    }

    private RhythmInput alternateDirection(RhythmCue cue) {
        long mixed = cue.id() ^ Long.rotateLeft(cue.timeMillis(), 21)
                ^ timeline.seed().hashCode();
        int index = Math.floorMod((int) (mixed ^ (mixed >>> 32)), DIRECTIONS.length);
        RhythmInput input = DIRECTIONS[index];
        if (input == previousInput) input = DIRECTIONS[(index + 1) % DIRECTIONS.length];
        return input;
    }

    private int minimumVerticalGap() {
        return switch (difficulty) {
            case EASY -> 6;
            case NORMAL -> 4;
            case HARD -> 3;
            case EXPERT -> 2;
        };
    }

    private static boolean isVertical(RhythmInput input) {
        return input == RhythmInput.JUMP || input == RhythmInput.SNEAK;
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
