package org.encinet.mik.module.music.rhythm;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Main-thread-owned score and judgement state for one player and playback identity. */
public final class RhythmGameSession {
    private final UUID playbackId;
    private final RhythmDifficulty difficulty;
    private final Set<Long> judged = new HashSet<>();
    private long missCursorMillis;
    private int score;
    private int combo;
    private int maximumCombo;
    private int hits;
    private int misses;
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
            if (judged.add(cue.id())) {
                registerMiss(cue.input(), playbackPositionMillis);
                newlyMissed++;
            }
        }
        missCursorMillis = safeCutoff + 1L;
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
                        position + goodWindow).stream()
                .filter(cue -> !judged.contains(cue.id()))
                .sorted(Comparator.comparingLong(cue ->
                        Math.abs(cue.timeMillis() - position)))
                .toList();
        RhythmCue matching = candidates.stream()
                .filter(cue -> pressed.contains(cue.input()))
                .findFirst().orElse(null);
        if (matching == null) {
            if (candidates.isEmpty()) return Result.NONE;
            RhythmCue wrong = candidates.getFirst();
            judged.add(wrong.id());
            registerMiss(pressed.getFirst(), position);
            return new Result(RhythmJudgement.MISS, wrong,
                    position - wrong.timeMillis(), 0);
        }

        judged.add(matching.id());
        long error = position - matching.timeMillis();
        long absoluteError = Math.abs(error);
        RhythmJudgement judgement = absoluteError <= difficulty.perfectWindowMillis()
                ? RhythmJudgement.PERFECT
                : absoluteError <= difficulty.greatWindowMillis()
                ? RhythmJudgement.GREAT : RhythmJudgement.GOOD;
        combo++;
        maximumCombo = Math.max(maximumCombo, combo);
        hits++;
        int delta = (int) Math.round(judgement.baseScore()
                * (100 + Math.min(combo, 50)) / 100.0
                * difficulty.scoreMultiplier());
        score += delta;
        lastJudgement = judgement;
        lastInput = matching.input();
        lastJudgementAtMillis = position;
        return new Result(judgement, matching, error, delta);
    }

    private void registerMiss(RhythmInput input, long position) {
        combo = 0;
        misses++;
        lastJudgement = RhythmJudgement.MISS;
        lastInput = input;
        lastJudgementAtMillis = position;
    }

    public View view() {
        return new View(score, combo, maximumCombo, hits, misses,
                lastJudgement, lastInput, lastJudgementAtMillis);
    }

    public boolean isJudged(long cueId) {
        return judged.contains(cueId);
    }

    /** Removes a cue that could not be presented with a fair visual lead time. */
    public void ignore(long cueId) {
        if (cueId == 0L) throw new IllegalArgumentException("cue id must not be zero");
        judged.add(cueId);
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

    public record View(int score, int combo, int maximumCombo, int hits, int misses,
                       RhythmJudgement lastJudgement, RhythmInput lastInput,
                       long lastJudgementAtMillis) {
    }
}
