package org.encinet.mik.feature.entertainment.music.rhythm.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe extracted rhythm data shared by playback and any number of game modes.
 * Audio analysis appends pulses while structured sources can publish a complete track.
 */
public final class RhythmTimeline implements RhythmTrack, RhythmExtractionSink {
    private static final long OCCURRENCE_MASK = 0x0000_0000_FFFF_FFFFL;

    private final String seed;
    private final List<RhythmBeat> beats = new ArrayList<>();
    private long nextId = 1L;
    private long analyzedThroughMillis;
    private boolean complete;
    private RhythmSource source = RhythmSource.AUDIO_ANALYSIS;
    private Loop loop;

    public RhythmTimeline(String seed) {
        if (seed == null || seed.isBlank()) {
            throw new IllegalArgumentException("timeline seed must not be blank");
        }
        this.seed = seed;
    }

    @Override
    public String seed() {
        return seed;
    }

    @Override
    public synchronized RhythmSource source() {
        return source;
    }

    @Override
    public synchronized boolean complete() {
        return complete;
    }

    @Override
    public synchronized boolean playable() {
        return !beats.isEmpty();
    }

    @Override
    public synchronized int baseBeatCount() {
        return beats.size();
    }

    @Override
    public synchronized long analyzedThroughMillis() {
        return analyzedThroughMillis;
    }

    @Override
    public synchronized void advanceAnalyzedThrough(long timeMillis) {
        analyzedThroughMillis = Math.max(analyzedThroughMillis, Math.max(0L, timeMillis));
    }

    @Override
    public synchronized RhythmBeat append(RhythmPulse pulse) {
        Objects.requireNonNull(pulse, "pulse");
        int insertion = lowerBound(pulse.timeMillis());
        for (int index = Math.max(0, insertion - 2);
             index < Math.min(beats.size(), insertion + 2); index++) {
            RhythmBeat existing = beats.get(index);
            if (Math.abs(existing.timeMillis() - pulse.timeMillis()) <= 20L) {
                advanceAnalyzedThrough(pulse.timeMillis());
                return existing;
            }
        }
        RhythmBeat beat = new RhythmBeat(nextId++, pulse);
        beats.add(insertion, beat);
        advanceAnalyzedThrough(pulse.timeMillis());
        return beat;
    }

    @Override
    public synchronized void publish(RhythmSource source, List<RhythmPulse> pulses,
                                     long durationMillis, long loopStartMillis,
                                     int maximumLoopCount) {
        this.source = Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pulses, "pulses");
        beats.clear();
        nextId = 1L;
        pulses.stream()
                .sorted(Comparator.comparingLong(RhythmPulse::timeMillis))
                .forEach(value -> beats.add(new RhythmBeat(nextId++, value)));
        long duration = Math.max(1L, durationMillis);
        analyzedThroughMillis = duration;
        complete = true;
        if (loopStartMillis >= 0L && loopStartMillis < duration) {
            loop = new Loop(loopStartMillis, duration,
                    maximumLoopCount == 0 ? -1 : maximumLoopCount);
        } else {
            loop = null;
        }
    }

    @Override
    public synchronized void markComplete(long durationMillis) {
        analyzedThroughMillis = Math.max(analyzedThroughMillis, Math.max(0L, durationMillis));
        complete = true;
    }

    /** Clears a failed attempt before the same track is decoded again from fresh media. */
    public synchronized void reset() {
        beats.clear();
        nextId = 1L;
        analyzedThroughMillis = 0L;
        complete = false;
        source = RhythmSource.AUDIO_ANALYSIS;
        loop = null;
    }

    /** Returns stable beat occurrences in the inclusive playback-relative window. */
    @Override
    public synchronized List<RhythmBeat> between(long fromMillis, long toMillis) {
        long from = Math.max(0L, fromMillis);
        long to = Math.max(from, toMillis);
        List<RhythmBeat> result = new ArrayList<>();
        addBaseRange(result, from, to);
        if (loop == null || to < loop.endMillis()) {
            return List.copyOf(result);
        }

        int loopStart = lowerBound(loop.startMillis());
        int loopEnd = lowerBound(loop.endMillis());
        if (loopStart == loopEnd) return List.copyOf(result);

        long cycleLength = loop.endMillis() - loop.startMillis();
        long firstRepeat = Math.max(1L,
                Math.floorDiv(Math.max(0L, from - loop.endMillis()), cycleLength) + 1L);
        long lastRepeat = Math.floorDiv(to - loop.endMillis(), cycleLength) + 1L;
        if (loop.maximumRepeats() >= 0) {
            lastRepeat = Math.min(lastRepeat, loop.maximumRepeats());
        }
        for (long repeat = firstRepeat; repeat <= lastRepeat;) {
            long repeatStart = loop.endMillis() + (repeat - 1L) * cycleLength;
            long baseFrom = loop.startMillis() + Math.max(0L, from - repeatStart);
            long baseTo = loop.startMillis() + Math.min(cycleLength - 1L, to - repeatStart);
            // Seek into the sorted source instead of scanning the whole song for
            // every player's small playback window, including late loop cycles.
            for (int index = Math.max(loopStart, lowerBound(baseFrom)); index < loopEnd; index++) {
                RhythmBeat base = beats.get(index);
                if (base.timeMillis() > baseTo) break;
                long occurrenceTime = repeatStart
                        + (base.timeMillis() - loop.startMillis());
                long occurrenceId = -((repeat << 32) | (base.id() & OCCURRENCE_MASK));
                result.add(base.occurrence(occurrenceId, occurrenceTime));
            }
            if (repeat == lastRepeat) break;
            repeat++;
        }
        result.sort(Comparator.comparingLong(RhythmBeat::timeMillis)
                .thenComparingLong(RhythmBeat::id));
        return List.copyOf(result);
    }

    private void addBaseRange(List<RhythmBeat> output, long from, long to) {
        for (int index = lowerBound(from); index < beats.size(); index++) {
            RhythmBeat beat = beats.get(index);
            if (beat.timeMillis() > to) break;
            output.add(beat);
        }
    }

    private int lowerBound(long timeMillis) {
        int low = 0;
        int high = beats.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (beats.get(middle).timeMillis() < timeMillis) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private record Loop(long startMillis, long endMillis, int maximumRepeats) {
        private Loop {
            if (startMillis < 0L || endMillis <= startMillis || maximumRepeats < -1) {
                throw new IllegalArgumentException("invalid rhythm loop");
            }
        }
    }
}
