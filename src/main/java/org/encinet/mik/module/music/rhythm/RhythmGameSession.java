package org.encinet.mik.module.music.rhythm;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread-owned score and judgement state for one player and playback identity. */
public final class RhythmGameSession {
    private final UUID playbackId;
    private final RhythmDifficulty difficulty;
    private final Map<Long, Long> judged = new HashMap<>();
    private long missCursorMillis;
    private long score;
    private long combo;
    private long maximumCombo;
    private long hits;
    private long misses;
    private RhythmJudgement lastJudgement = RhythmJudgement.NONE;
    private RhythmInput lastInput;
    private long lastJudgementAtMillis = Long.MIN_VALUE / 4L;

    public RhythmGameSession(UUID playbackId, long joinedAtMillis,
                             RhythmDifficulty difficulty) {
        this.playbackId = Objects.requireNonNull(playbackId, "playbackId");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.missCursorMillis = Math.max(0L, joinedAtMillis);
    }

    public UUID playbackId() {
        return playbackId;
    }

    public RhythmDifficulty difficulty() {
        return difficulty;
    }

    /**
     * Starts judgement after a visual entrance without charging the player for
     * cues that became unplayable while the scene was opening.
     */
    public void beginAt(long playbackPositionMillis) {
        long earliestPlayable = Math.max(0L,
                playbackPositionMillis - difficulty.goodWindowMillis());
        missCursorMillis = Math.max(missCursorMillis, earliestPlayable);
    }

    public int advance(long playbackPositionMillis, RhythmChartView chart) {
        Objects.requireNonNull(chart, "chart");
        long cutoff = Math.max(0L,
                playbackPositionMillis - difficulty.goodWindowMillis());
        long safeCutoff = chart.complete()
                ? cutoff : Math.min(cutoff, chart.analyzedThroughMillis());
        if (safeCutoff < missCursorMillis) return 0;
        int newlyMissed = 0;
        for (RhythmCue cue : chart.between(missCursorMillis, safeCutoff)) {
            if (markJudged(cue)) {
                registerMiss(cue.input(), playbackPositionMillis);
                newlyMissed++;
            }
        }
        missCursorMillis = safeCutoff == Long.MAX_VALUE
                ? Long.MAX_VALUE : safeCutoff + 1L;
        return newlyMissed;
    }

    public Result input(RhythmInput input, long playbackPositionMillis,
                        RhythmChartView chart) {
        return input(List.of(Objects.requireNonNull(input, "input")),
                playbackPositionMillis, chart);
    }

    /** Treats simultaneous directional presses as one input and matches before penalizing. */
    public Result input(List<RhythmInput> inputs, long playbackPositionMillis,
                        RhythmChartView chart) {
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.isEmpty()) return Result.NONE;
        List<RhythmInput> pressed = inputs.stream().map(value ->
                Objects.requireNonNull(value, "inputs must not contain null")).distinct().toList();
        Objects.requireNonNull(chart, "chart");
        long position = Math.max(0L, playbackPositionMillis);
        long goodWindow = difficulty.goodWindowMillis();
        List<RhythmCue> candidates = chart.between(
                        Math.max(0L, position - goodWindow),
                        saturatedAdd(position, goodWindow)).stream()
                .filter(cue -> !judged.containsKey(cue.id()))
                .sorted(Comparator.comparingLong(cue ->
                        Math.abs(cue.timeMillis() - position)))
                .toList();
        RhythmCue matching = candidates.stream()
                .filter(cue -> pressed.contains(cue.input()))
                .findFirst().orElse(null);
        if (matching == null) {
            if (candidates.isEmpty()) return Result.NONE;
            RhythmCue wrong = candidates.getFirst();
            markJudged(wrong);
            registerMiss(pressed.getFirst(), position);
            return new Result(RhythmJudgement.MISS, wrong,
                    position - wrong.timeMillis(), 0);
        }

        return registerHit(matching, position);
    }

    /**
     * Judges one explicitly aimed cue without projecting the click onto a lane.
     * Empty-space clicks remain neutral and the normal expiry path owns misses.
     */
    public Result hit(RhythmCue aimedCue, long playbackPositionMillis,
                      RhythmChartView chart) {
        Objects.requireNonNull(aimedCue, "aimedCue");
        Objects.requireNonNull(chart, "chart");
        long position = Math.max(0L, playbackPositionMillis);
        long goodWindow = difficulty.goodWindowMillis();
        RhythmCue matching = chart.between(
                        Math.max(0L, position - goodWindow),
                        saturatedAdd(position, goodWindow)).stream()
                .filter(cue -> cue.id() == aimedCue.id()
                        && cue.timeMillis() == aimedCue.timeMillis()
                        && !judged.containsKey(cue.id()))
                .findFirst().orElse(null);
        return matching == null ? Result.NONE : registerHit(matching, position);
    }

    private Result registerHit(RhythmCue matching, long position) {
        markJudged(matching);
        long error = position - matching.timeMillis();
        long absoluteError = Math.abs(error);
        RhythmJudgement judgement = absoluteError <= difficulty.perfectWindowMillis()
                ? RhythmJudgement.PERFECT
                : absoluteError <= difficulty.greatWindowMillis()
                ? RhythmJudgement.GREAT : RhythmJudgement.GOOD;
        combo = incrementSaturated(combo);
        maximumCombo = Math.max(maximumCombo, combo);
        hits = incrementSaturated(hits);
        int delta = (int) Math.round(judgement.baseScore()
                * (100L + Math.min(combo, 50L)) / 100.0
                * difficulty.scoreMultiplier());
        score = score > Long.MAX_VALUE - delta ? Long.MAX_VALUE : score + delta;
        lastJudgement = judgement;
        lastInput = matching.input();
        lastJudgementAtMillis = position;
        return new Result(judgement, matching, error, delta);
    }

    private void registerMiss(RhythmInput input, long position) {
        combo = 0;
        misses = incrementSaturated(misses);
        lastJudgement = RhythmJudgement.MISS;
        lastInput = input;
        lastJudgementAtMillis = position;
    }

    public View view() {
        return new View(score, combo, maximumCombo, hits, misses,
                lastJudgement, lastInput, lastJudgementAtMillis);
    }

    public boolean isJudged(long cueId) {
        return judged.containsKey(cueId);
    }

    /** Removes a cue that could not be presented with a fair visual lead time. */
    public void ignore(RhythmCue cue) {
        markJudged(Objects.requireNonNull(cue, "cue"));
    }

    /** Releases judgement identities after the chart has discarded the same history. */
    void discardBefore(long timeMillis) {
        long cutoff = Math.max(0L, timeMillis);
        judged.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    int retainedJudgementCount() {
        return judged.size();
    }

    private boolean markJudged(RhythmCue cue) {
        return judged.putIfAbsent(cue.id(), cue.timeMillis()) == null;
    }

    private static long incrementSaturated(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static long saturatedAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment
                ? Long.MAX_VALUE : value + increment;
    }

    public record Result(RhythmJudgement judgement, RhythmCue cue,
                         long timingErrorMillis, int scoreDelta) {
        public static final Result NONE = new Result(
                RhythmJudgement.NONE, null, 0L, 0);

        public Result {
            judgement = Objects.requireNonNull(judgement, "judgement");
            if (judgement == RhythmJudgement.NONE && cue != null) {
                throw new IllegalArgumentException("an empty judgement cannot own a cue");
            }
        }
    }

    public record View(long score, long combo, long maximumCombo, long hits, long misses,
                       RhythmJudgement lastJudgement, RhythmInput lastInput,
                       long lastJudgementAtMillis) {
    }
}
