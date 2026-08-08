package org.encinet.mik.module.music.rhythm.calibration;

/** One cue at the monotonic instant its sound or visual packet left the server. */
public record RhythmCuePresentation(long cueId, long cycleIndex, int cueIndex,
                                    int cuesPerCycle, long presentedAtNanos,
                                    double strength) {
    public RhythmCuePresentation {
        if (cueId < 0L || cycleIndex < 0L || cueIndex < 0
                || cuesPerCycle < 1 || cueIndex >= cuesPerCycle) {
            throw new IllegalArgumentException("invalid calibration cue identity");
        }
        if (!Double.isFinite(strength) || strength < 0.0 || strength > 1.0) {
            throw new IllegalArgumentException("strength must be within 0..1");
        }
    }
}
