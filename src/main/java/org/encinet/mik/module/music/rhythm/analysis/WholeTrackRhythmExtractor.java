package org.encinet.mik.module.music.rhythm.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PCM extractor that deliberately waits for the complete song before emitting beats.
 *
 * <p>Two onset detectors observe the same decoded audio. A permissive detector
 * supplies weak-beat candidates while a stricter whole-track profile supplies
 * trustworthy anchors and a safe fallback. On flush, {@link RhythmBeatTracker}
 * uses whole-track tempo and phase evidence to publish the final pulses.</p>
 */
public final class WholeTrackRhythmExtractor implements PcmRhythmExtractor {
    private final RhythmExtractionSink output;
    private final CollectingSink candidates = new CollectingSink();
    private final CollectingSink anchors = new CollectingSink();
    private final RhythmOnsetDetector candidateDetector;
    private final RhythmOnsetDetector anchorDetector;
    private final AtomicBoolean finished = new AtomicBoolean();

    public WholeTrackRhythmExtractor(RhythmExtractionSink output, int sampleRate,
                                     long initialPositionMillis) {
        this.output = Objects.requireNonNull(output, "output");
        candidateDetector = RhythmOnsetDetector.wholeTrackCandidates(
                candidates, sampleRate, initialPositionMillis);
        anchorDetector = RhythmOnsetDetector.wholeTrackAnchors(
                anchors, sampleRate, initialPositionMillis);
    }

    @Override
    public void accept(float[][] channels, int offset, int length) {
        if (finished.get()) return;
        candidateDetector.accept(channels, offset, length);
        anchorDetector.accept(channels, offset, length);
    }

    @Override
    public void seek(long positionMillis) {
        if (finished.get()) return;
        candidates.clear();
        anchors.clear();
        candidateDetector.seek(positionMillis);
        anchorDetector.seek(positionMillis);
    }

    @Override
    public void flush() {
        if (!finished.compareAndSet(false, true)) return;
        candidateDetector.flush();
        anchorDetector.flush();
        long analyzedThrough = Math.max(candidates.analyzedThroughMillis(),
                anchors.analyzedThroughMillis());
        List<RhythmPulse> refined = RhythmBeatTracker.refine(
                candidates.pulses(), anchors.pulses(), analyzedThrough);
        refined.forEach(output::append);
        output.advanceAnalyzedThrough(analyzedThrough);
    }

    private static final class CollectingSink implements RhythmExtractionSink {
        private final List<RhythmPulse> pulses = new ArrayList<>();
        private long analyzedThroughMillis;
        private long nextId = 1L;

        @Override
        public RhythmBeat append(RhythmPulse pulse) {
            pulses.add(Objects.requireNonNull(pulse, "pulse"));
            analyzedThroughMillis = Math.max(analyzedThroughMillis,
                    pulse.timeMillis());
            return new RhythmBeat(nextId++, pulse);
        }

        @Override
        public void advanceAnalyzedThrough(long timeMillis) {
            analyzedThroughMillis = Math.max(analyzedThroughMillis,
                    Math.max(0L, timeMillis));
        }

        @Override
        public void publish(RhythmSource source, List<RhythmPulse> replacement,
                            long durationMillis, long loopStartMillis,
                            int maximumLoopCount) {
            pulses.clear();
            pulses.addAll(replacement);
            analyzedThroughMillis = Math.max(0L, durationMillis);
        }

        @Override
        public void markComplete(long durationMillis) {
            advanceAnalyzedThrough(durationMillis);
        }

        private List<RhythmPulse> pulses() {
            return List.copyOf(pulses);
        }

        private long analyzedThroughMillis() {
            return analyzedThroughMillis;
        }

        private void clear() {
            pulses.clear();
            analyzedThroughMillis = 0L;
            nextId = 1L;
        }
    }
}
