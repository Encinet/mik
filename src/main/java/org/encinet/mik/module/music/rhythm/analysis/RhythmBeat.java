package org.encinet.mik.module.music.rhythm.analysis;

/** One stable occurrence of an extracted pulse on a playback-relative track. */
public record RhythmBeat(
        long id,
        long timeMillis,
        double strength,
        double stereoBalance,
        double toneBalance,
        long signature
) {
    public RhythmBeat(long id, RhythmPulse pulse) {
        this(id, pulse.timeMillis(), pulse.strength(), pulse.stereoBalance(),
                pulse.toneBalance(), pulse.signature());
    }

    public RhythmBeat {
        if (id == 0L) throw new IllegalArgumentException("beat id must not be zero");
        // Reuse the extractor-output contract for all feature validation.
        new RhythmPulse(timeMillis, strength, stereoBalance, toneBalance, signature);
    }

    RhythmBeat occurrence(long occurrenceId, long occurrenceTime) {
        return new RhythmBeat(occurrenceId, occurrenceTime, strength,
                stereoBalance, toneBalance, signature);
    }
}
