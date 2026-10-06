package org.encinet.mik.module.music.rhythm;

import java.util.Objects;
import java.util.Optional;

/** One complete, validated set of channel and input-modality calibration data. */
record RhythmCalibrationResult(RhythmCalibrationProfiles profiles,
                               int pointerInputDeltaMillis) {
    static final int MINIMUM_POINTER_DELTA_MILLIS = -200;
    static final int MAXIMUM_POINTER_DELTA_MILLIS = 200;

    RhythmCalibrationResult {
        profiles = Objects.requireNonNull(profiles, "profiles");
        pointerInputDeltaMillis = Math.clamp(pointerInputDeltaMillis,
                MINIMUM_POINTER_DELTA_MILLIS,
                MAXIMUM_POINTER_DELTA_MILLIS);
    }

    static RhythmCalibrationResult fromTests(
            int visualTapOffsetMillis,
            int minecraftTapOffsetMillis,
            int plasmoTapOffsetMillis,
            int pointerVisualTapOffsetMillis) {
        return new RhythmCalibrationResult(
                RhythmCalibrationProfiles.fromTests(visualTapOffsetMillis,
                        minecraftTapOffsetMillis, plasmoTapOffsetMillis),
                RhythmLatencyProfile.pointerInputDelta(
                        visualTapOffsetMillis, pointerVisualTapOffsetMillis));
    }

    /**
     * Decodes the all-or-nothing persistent representation. Partial or corrupt
     * data must never make a player appear calibrated.
     */
    static Optional<RhythmCalibrationResult> fromStored(
            Integer minecraftJudgementMillis,
            Integer minecraftAnimationMillis,
            Integer plasmoJudgementMillis,
            Integer plasmoAnimationMillis,
            Integer pointerInputDeltaMillis) {
        if (minecraftJudgementMillis == null || minecraftAnimationMillis == null
                || plasmoJudgementMillis == null || plasmoAnimationMillis == null
                || pointerInputDeltaMillis == null) {
            return Optional.empty();
        }
        if (!validJudgement(minecraftJudgementMillis)
                || !validJudgement(plasmoJudgementMillis)
                || !validAnimation(minecraftAnimationMillis)
                || !validAnimation(plasmoAnimationMillis)
                || pointerInputDeltaMillis < MINIMUM_POINTER_DELTA_MILLIS
                || pointerInputDeltaMillis > MAXIMUM_POINTER_DELTA_MILLIS) {
            return Optional.empty();
        }
        return Optional.of(new RhythmCalibrationResult(
                new RhythmCalibrationProfiles(
                        new RhythmLatencyProfile(minecraftJudgementMillis,
                                minecraftAnimationMillis),
                        new RhythmLatencyProfile(plasmoJudgementMillis,
                                plasmoAnimationMillis)),
                pointerInputDeltaMillis));
    }

    private static boolean validJudgement(int value) {
        return value >= RhythmLatencyProfile.MINIMUM_JUDGEMENT_OFFSET_MILLIS
                && value <= RhythmLatencyProfile.MAXIMUM_JUDGEMENT_OFFSET_MILLIS;
    }

    private static boolean validAnimation(int value) {
        return value >= RhythmLatencyProfile.MINIMUM_ANIMATION_OFFSET_MILLIS
                && value <= RhythmLatencyProfile.MAXIMUM_ANIMATION_OFFSET_MILLIS;
    }
}
