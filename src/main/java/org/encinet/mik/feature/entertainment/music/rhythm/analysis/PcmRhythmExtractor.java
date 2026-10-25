package org.encinet.mik.feature.entertainment.music.rhythm.analysis;

/** Stateful extractor for decoded floating-point PCM chunks. */
public interface PcmRhythmExtractor {
    void accept(float[][] channels, int offset, int length);

    void seek(long positionMillis);

    void flush();
}
