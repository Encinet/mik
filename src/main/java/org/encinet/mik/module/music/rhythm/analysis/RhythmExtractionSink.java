package org.encinet.mik.module.music.rhythm.analysis;

import java.util.List;

/** Publication boundary used by PCM and structured rhythm extractors. */
public interface RhythmExtractionSink {
    RhythmBeat append(RhythmPulse pulse);

    void advanceAnalyzedThrough(long timeMillis);

    void publish(RhythmSource source, List<RhythmPulse> pulses, long durationMillis,
                 long loopStartMillis, int maximumLoopCount);

    void markComplete(long durationMillis);
}
