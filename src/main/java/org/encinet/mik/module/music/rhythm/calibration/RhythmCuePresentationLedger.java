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
        discardBefore(presentation.presentedAtNanos() - RETENTION_NANOS);
    }

    public void addAll(List<RhythmCuePresentation> values) {
        values.forEach(this::add);
    }

    public Optional<RhythmCuePresentation> closest(long adjustedInputAtNanos,
                                                   java.util.function.LongPredicate sampled) {
        return presentations.stream()
                .filter(value -> !sampled.test(value.cueId()))
                .min(Comparator.comparingLong(value -> distance(
                        value.presentedAtNanos(), adjustedInputAtNanos)));
    }

    public List<RhythmCuePresentation> snapshot() {
        return List.copyOf(new ArrayList<>(presentations));
    }

    public void clear() {
        presentations.clear();
    }

    private void discardBefore(long cutoffNanos) {
        while (!presentations.isEmpty()
                && presentations.getFirst().presentedAtNanos() < cutoffNanos) {
            presentations.removeFirst();
        }
    }

    private static long distance(long first, long second) {
        long difference = first - second;
        if (difference == Long.MIN_VALUE) return Long.MAX_VALUE;
        return Math.abs(difference);
    }
}
