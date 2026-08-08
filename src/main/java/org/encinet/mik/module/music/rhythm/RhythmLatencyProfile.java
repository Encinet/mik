package org.encinet.mik.module.music.rhythm;

/** Separates end-to-end tap compensation from audio/visual scene alignment. */
record RhythmLatencyProfile(int judgementOffsetMillis, int animationOffsetMillis) {
    static final int MINIMUM_ANIMATION_OFFSET_MILLIS = -350;
    static final int MAXIMUM_ANIMATION_OFFSET_MILLIS = 350;

    RhythmLatencyProfile {
        judgementOffsetMillis = Math.clamp(judgementOffsetMillis,
                RhythmLatencyCalibration.MINIMUM_OFFSET_MILLIS,
                RhythmLatencyCalibration.MAXIMUM_OFFSET_MILLIS);
        animationOffsetMillis = Math.clamp(animationOffsetMillis,
                MINIMUM_ANIMATION_OFFSET_MILLIS,
                MAXIMUM_ANIMATION_OFFSET_MILLIS);
    }

    /**
     * The audio test gives the compensation used for scoring. Subtracting the
     * visual-test result removes shared reaction/input time and leaves the
     * amount by which the animation must be delayed (or advanced).
     */
    static RhythmLatencyProfile fromTests(int visualTapOffsetMillis,
                                          int audioTapOffsetMillis) {
        long difference = (long) audioTapOffsetMillis - visualTapOffsetMillis;
        int animation = (int) Math.clamp(difference,
                MINIMUM_ANIMATION_OFFSET_MILLIS,
                MAXIMUM_ANIMATION_OFFSET_MILLIS);
        return new RhythmLatencyProfile(audioTapOffsetMillis,
                animation);
    }

    /** Keeps an already measured visual baseline aligned after audio-path fine tuning. */
    RhythmLatencyProfile withJudgementOffset(int sourceOffsetMillis) {
        long adjustedAnimation = (long) animationOffsetMillis
                + sourceOffsetMillis - judgementOffsetMillis;
        int safeAnimation = (int) Math.clamp(adjustedAnimation,
                MINIMUM_ANIMATION_OFFSET_MILLIS,
                MAXIMUM_ANIMATION_OFFSET_MILLIS);
        return new RhythmLatencyProfile(sourceOffsetMillis, safeAnimation);
    }
}
