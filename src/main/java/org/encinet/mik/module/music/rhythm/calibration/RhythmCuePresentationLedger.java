package org.encinet.mik.module.music.rhythm.calibration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Main-thread ledger used to associate one adjusted input with one real cue. */
public final class RhythmCuePresentationLedger {
    private static final long RETENTION_NANOS = 4_000_000_000L;

    private final ArrayDeque<RhythmCuePresentation> presentations =
            new ArrayDeque<>();

    public void add(RhythmCuePresentation presentation) {
        java.util.Objects.requireNonNull(presentation, "presentation");
        presentations.removeIf(value -> value.cueId() == presentation.cueId());
        presentations.addLast(presentation);
        discardOlderThan(presentation.presentedAtNanos());
    }

    public void addAll(List<RhythmCuePresentation> values) {
        values.forEach(this::add);
    }

    public Optional<RhythmCuePresentation> closest(long adjustedInputAtNanos,
                                                   java.util.function.LongPredicate sampled) {
        return closestWithin(adjustedInputAtNanos, Long.MAX_VALUE, sampled);
    }

    /** Finds one unsampled cue only when it is inside the explicit capture window. */
    public Optional<RhythmCuePresentation> closestWithin(
            long adjustedInputAtNanos, long maximumDistanceNanos,
            java.util.function.LongPredicate sampled) {
        if (maximumDistanceNanos < 0L) {
            throw new IllegalArgumentException(
                    "maximum distance must not be negative");
        }
        java.util.Objects.requireNonNull(sampled, "sampled");
        return presentations.stream()
                .filter(value -> !sampled.test(value.cueId()))
                .filter(value -> distance(value.presentedAtNanos(),
                        adjustedInputAtNanos) <= maximumDistanceNanos)
                .min(Comparator.comparingLong(value -> distance(
                        value.presentedAtNanos(), adjustedInputAtNanos)));
    }

    public List<RhythmCuePresentation> snapshot() {
        return List.copyOf(new ArrayList<>(presentations));
    }

    public void clear() {
        presentations.clear();
    }

    private void discardOlderThan(long referenceNanos) {
        presentations.removeIf(value -> {
            long elapsed = referenceNanos - value.presentedAtNanos();
            return elapsed >= 0L && elapsed > RETENTION_NANOS;
        });
    }

    private static long distance(long first, long second) {
        long difference = first - second;
        if (difference == Long.MIN_VALUE) return Long.MAX_VALUE;
        return Math.abs(difference);
    }
}
