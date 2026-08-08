package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmLatencyProfileTest {

    @Test
    void separatesSharedReactionTimeFromAudioVisualSkew() {
        RhythmLatencyProfile profile = RhythmLatencyProfile.fromTests(55, 125);

        assertEquals(125, profile.judgementOffsetMillis());
        assertEquals(70, profile.animationOffsetMillis());
    }

    @Test
    void supportsAudioThatArrivesBeforeTheVisualCue() {
        RhythmLatencyProfile profile = RhythmLatencyProfile.fromTests(140, 80);

        assertEquals(80, profile.judgementOffsetMillis());
        assertEquals(-60, profile.animationOffsetMillis());
    }

    @Test
    void clampsBothPersistedClockComponents() {
        RhythmLatencyProfile profile = new RhythmLatencyProfile(5_000, -5_000);

        assertEquals(RhythmLatencyCalibration.MAXIMUM_OFFSET_MILLIS,
                profile.judgementOffsetMillis());
        assertEquals(RhythmLatencyProfile.MINIMUM_ANIMATION_OFFSET_MILLIS,
                profile.animationOffsetMillis());
    }

    @Test
    void sourceFineTuningMovesJudgementAndAnimationTogether() {
        RhythmLatencyProfile baseline = new RhythmLatencyProfile(90, 35);

        RhythmLatencyProfile tuned = baseline.withJudgementOffset(130);

        assertEquals(130, tuned.judgementOffsetMillis());
        assertEquals(75, tuned.animationOffsetMillis());
    }

    @Test
    void pointerDeltaMovesOnlyTheJudgementClock() {
        RhythmLatencyProfile baseline = new RhythmLatencyProfile(90, 35);

        RhythmLatencyProfile pointer = baseline.withInputDelta(-18);

        assertEquals(72, pointer.judgementOffsetMillis());
        assertEquals(35, pointer.animationOffsetMillis());
        assertEquals(-18, RhythmLatencyProfile.pointerInputDelta(108, 90));
        assertEquals(200, RhythmLatencyProfile.pointerInputDelta(-250, 350));
    }
}
