package org.encinet.mik.module.music.rhythm;

/** Per-player chart density, judgement timing, and score policy. */
public enum RhythmDifficulty {
    EASY(500L, 120L, 200L, 300L, 0.80),
    NORMAL(250L, 90L, 150L, 220L, 1.00),
    HARD(170L, 70L, 120L, 165L, 1.15),
    EXPERT(130L, 55L, 90L, 125L, 1.35);

    private final long minimumCueSpacingMillis;
    private final long perfectWindowMillis;
    private final long greatWindowMillis;
    private final long goodWindowMillis;
    private final double scoreMultiplier;

    RhythmDifficulty(long minimumCueSpacingMillis,
                     long perfectWindowMillis,
                     long greatWindowMillis,
                     long goodWindowMillis,
                     double scoreMultiplier) {
        if (minimumCueSpacingMillis < 1L
                || perfectWindowMillis < 1L
                || perfectWindowMillis >= greatWindowMillis
                || greatWindowMillis >= goodWindowMillis
                || goodWindowMillis > minimumCueSpacingMillis
                || !Double.isFinite(scoreMultiplier) || scoreMultiplier <= 0.0) {
            throw new IllegalArgumentException("invalid rhythm difficulty policy");
        }
        this.minimumCueSpacingMillis = minimumCueSpacingMillis;
        this.perfectWindowMillis = perfectWindowMillis;
        this.greatWindowMillis = greatWindowMillis;
        this.goodWindowMillis = goodWindowMillis;
        this.scoreMultiplier = scoreMultiplier;
    }

    public long minimumCueSpacingMillis() {
        return minimumCueSpacingMillis;
    }

    public long perfectWindowMillis() {
        return perfectWindowMillis;
    }

    public long greatWindowMillis() {
        return greatWindowMillis;
    }

    public long goodWindowMillis() {
        return goodWindowMillis;
    }

    public double scoreMultiplier() {
        return scoreMultiplier;
    }

    public double maximumCuesPerSecond() {
        return 1000.0 / minimumCueSpacingMillis;
    }
}
