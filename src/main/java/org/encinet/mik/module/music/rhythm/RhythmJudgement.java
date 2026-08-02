package org.encinet.mik.module.music.rhythm;

/** Server-side timing result ordered from strongest hit to no applicable cue. */
public enum RhythmJudgement {
    PERFECT(true, 1000),
    GREAT(true, 700),
    GOOD(true, 400),
    MISS(false, 0),
    NONE(false, 0);

    private final boolean hit;
    private final int baseScore;

    RhythmJudgement(boolean hit, int baseScore) {
        this.hit = hit;
        this.baseScore = baseScore;
    }

    public boolean hit() {
        return hit;
    }

    int baseScore() {
        return baseScore;
    }
}
