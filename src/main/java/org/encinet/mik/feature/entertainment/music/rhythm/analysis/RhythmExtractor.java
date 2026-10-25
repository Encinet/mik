package org.encinet.mik.feature.entertainment.music.rhythm.analysis;

/** Converts one structured music source into mode-independent rhythm pulses. */
@FunctionalInterface
public interface RhythmExtractor<S> {
    void extract(S source, RhythmExtractionSink output);
}
