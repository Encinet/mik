package org.encinet.mik.module.music.rhythm;

import java.util.Objects;

/** One stable action and its mode-independent musical features. */
public record RhythmCue(long id, long timeMillis, RhythmInput input, double strength,
                        double stereoBalance, double toneBalance, long signature) {
    public RhythmCue(long id, long timeMillis, RhythmInput input, double strength) {
        this(id, timeMillis, input, strength, 0.0, 0.0, id);
    }

    public RhythmCue {
        if (id == 0L) throw new IllegalArgumentException("cue id must not be zero");
        if (timeMillis < 0L) throw new IllegalArgumentException("cue time must not be negative");
        input = Objects.requireNonNull(input, "input");
        if (!Double.isFinite(strength) || strength < 0.0 || strength > 1.0) {
            throw new IllegalArgumentException("cue strength must be between zero and one");
        }
        if (!Double.isFinite(stereoBalance) || stereoBalance < -1.0
                || stereoBalance > 1.0 || !Double.isFinite(toneBalance)
                || toneBalance < -1.0 || toneBalance > 1.0) {
            throw new IllegalArgumentException("cue musical balances must be between -1 and 1");
        }
    }

    RhythmCue occurrence(long occurrenceId, long occurrenceTime) {
        return new RhythmCue(occurrenceId, occurrenceTime, input, strength,
                stereoBalance, toneBalance, signature);
    }
}
