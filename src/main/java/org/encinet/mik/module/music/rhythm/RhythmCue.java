package org.encinet.mik.module.music.rhythm;

import java.util.Objects;

/** One stable action on a playback-relative rhythm timeline. */
public record RhythmCue(long id, long timeMillis, RhythmInput input, double strength) {
    public RhythmCue {
        if (id == 0L) throw new IllegalArgumentException("cue id must not be zero");
        if (timeMillis < 0L) throw new IllegalArgumentException("cue time must not be negative");
        input = Objects.requireNonNull(input, "input");
        if (!Double.isFinite(strength) || strength < 0.0 || strength > 1.0) {
            throw new IllegalArgumentException("cue strength must be between zero and one");
        }
    }

    RhythmCue occurrence(long occurrenceId, long occurrenceTime) {
        return new RhythmCue(occurrenceId, occurrenceTime, input, strength);
    }
}
