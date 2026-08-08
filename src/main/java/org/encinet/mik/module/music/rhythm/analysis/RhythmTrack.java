package org.encinet.mik.module.music.rhythm.analysis;

import java.util.List;

/** Read-only, mode-independent rhythm data shared by all games on one playback. */
public interface RhythmTrack {
    String seed();

    RhythmSource source();

    boolean complete();

    boolean playable();

    int baseBeatCount();

    long analyzedThroughMillis();

    /** Returns stable beat occurrences in the inclusive playback-relative window. */
    List<RhythmBeat> between(long fromMillis, long toMillis);
}
