package org.encinet.mik.module.music.rhythm.analysis;

/** Creates an isolated incremental extractor for one complete-media decoder chain. */
@FunctionalInterface
public interface PcmRhythmExtractorFactory {
    PcmRhythmExtractor create(RhythmExtractionSink output, int sampleRate,
                              long initialPositionMillis);
}
