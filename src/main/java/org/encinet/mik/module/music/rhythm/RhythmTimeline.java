package org.encinet.mik.module.music.rhythm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe, incrementally published rhythm chart shared by playback and game sessions.
 * Audio analysis appends cues while NBS publishes a complete chart with optional loops.
 */
public final class RhythmTimeline {
    private static final long OCCURRENCE_MASK = 0x0000_0000_FFFF_FFFFL;

    private final String seed;
    private final List<RhythmCue> cues = new ArrayList<>();
    private long nextId = 1L;
    private long analyzedThroughMillis;
    private boolean complete;
    private Source source = Source.AUDIO_ANALYSIS;
    private Loop loop;

    public RhythmTimeline(String seed) {
        if (seed == null || seed.isBlank()) {
            throw new IllegalArgumentException("timeline seed must not be blank");
        }
        this.seed = seed;
    }

    public String seed() {
        return seed;
    }

    public synchronized Source source() {
        return source;
    }

    public synchronized boolean complete() {
        return complete;
    }

    public synchronized boolean playable() {
        return !cues.isEmpty();
    }

    public synchronized int baseCueCount() {
        return cues.size();
    }

    public synchronized long analyzedThroughMillis() {
        return analyzedThroughMillis;
    }

    public synchronized void advanceAnalyzedThrough(long timeMillis) {
        analyzedThroughMillis = Math.max(analyzedThroughMillis, Math.max(0L, timeMillis));
    }

    public synchronized RhythmCue append(long timeMillis, RhythmInput input, double strength) {
        long time = Math.max(0L, timeMillis);
        Objects.requireNonNull(input, "input");
        int insertion = lowerBound(time);
        for (int index = Math.max(0, insertion - 2);
             index < Math.min(cues.size(), insertion + 2); index++) {
            RhythmCue existing = cues.get(index);
            if (existing.input() == input && Math.abs(existing.timeMillis() - time) <= 20L) {
                advanceAnalyzedThrough(time);
                return existing;
            }
        }
        RhythmCue cue = new RhythmCue(nextId++, time, input, Math.clamp(strength, 0.0, 1.0));
        cues.add(insertion, cue);
        advanceAnalyzedThrough(time);
        return cue;
    }

    public synchronized void publishNbs(List<TimedInput> chart, long durationMillis,
                                        long loopStartMillis, int maximumLoopCount) {
        Objects.requireNonNull(chart, "chart");
        cues.clear();
        nextId = 1L;
        chart.stream()
                .sorted(Comparator.comparingLong(TimedInput::timeMillis))
                .forEach(value -> cues.add(new RhythmCue(nextId++, value.timeMillis(),
                        value.input(), value.strength())));
        long duration = Math.max(1L, durationMillis);
        analyzedThroughMillis = duration;
        source = Source.NBS_NOTES;
        complete = true;
        if (loopStartMillis >= 0L && loopStartMillis < duration) {
            loop = new Loop(loopStartMillis, duration,
                    maximumLoopCount == 0 ? -1 : maximumLoopCount);
        } else {
            loop = null;
        }
    }

    public synchronized void markAudioComplete(long durationMillis) {
        analyzedThroughMillis = Math.max(analyzedThroughMillis, Math.max(0L, durationMillis));
        complete = true;
    }

    /** Returns stable cue occurrences in the inclusive playback-relative window. */
    public synchronized List<RhythmCue> between(long fromMillis, long toMillis) {
        long from = Math.max(0L, fromMillis);
        long to = Math.max(from, toMillis);
        List<RhythmCue> result = new ArrayList<>();
        addBaseRange(result, from, to);
        if (loop == null || to < loop.endMillis()) {
            return List.copyOf(result);
        }

        long cycleLength = loop.endMillis() - loop.startMillis();
        long firstRepeat = Math.max(1L,
                Math.floorDiv(Math.max(0L, from - loop.endMillis()), cycleLength) + 1L);
        long lastRepeat = Math.floorDiv(to - loop.endMillis(), cycleLength) + 1L;
        if (loop.maximumRepeats() >= 0) {
            lastRepeat = Math.min(lastRepeat, loop.maximumRepeats());
        }
        for (long repeat = firstRepeat; repeat <= lastRepeat; repeat++) {
            long repeatStart = loop.endMillis() + (repeat - 1L) * cycleLength;
            for (RhythmCue base : cues) {
                if (base.timeMillis() < loop.startMillis()) continue;
                if (base.timeMillis() >= loop.endMillis()) break;
                long occurrenceTime = repeatStart
                        + base.timeMillis() - loop.startMillis();
                if (occurrenceTime < from || occurrenceTime > to) continue;
                long occurrenceId = -((repeat << 32) | (base.id() & OCCURRENCE_MASK));
                result.add(base.occurrence(occurrenceId, occurrenceTime));
            }
        }
        result.sort(Comparator.comparingLong(RhythmCue::timeMillis)
                .thenComparingLong(RhythmCue::id));
        return List.copyOf(result);
    }

    private void addBaseRange(List<RhythmCue> output, long from, long to) {
        for (int index = lowerBound(from); index < cues.size(); index++) {
            RhythmCue cue = cues.get(index);
            if (cue.timeMillis() > to) break;
            output.add(cue);
        }
    }

    private int lowerBound(long timeMillis) {
        int low = 0;
        int high = cues.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cues.get(middle).timeMillis() < timeMillis) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    public enum Source {
        AUDIO_ANALYSIS,
        NBS_NOTES
    }

    public record TimedInput(long timeMillis, RhythmInput input, double strength) {
        public TimedInput {
            if (timeMillis < 0L) throw new IllegalArgumentException("time must not be negative");
            input = Objects.requireNonNull(input, "input");
            if (!Double.isFinite(strength) || strength < 0.0 || strength > 1.0) {
                throw new IllegalArgumentException("strength must be between zero and one");
            }
        }
    }

    private record Loop(long startMillis, long endMillis, int maximumRepeats) {
        private Loop {
            if (startMillis < 0L || endMillis <= startMillis || maximumRepeats < -1) {
                throw new IllegalArgumentException("invalid rhythm loop");
            }
        }
    }
}
