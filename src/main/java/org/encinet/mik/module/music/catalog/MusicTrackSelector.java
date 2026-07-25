package org.encinet.mik.module.music.catalog;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Selects uniformly from tracks that satisfy a caller-defined constraint. */
public final class MusicTrackSelector {

    private final IntUnaryOperator randomIndex;

    public MusicTrackSelector() {
        this(bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    MusicTrackSelector(IntUnaryOperator randomIndex) {
        this.randomIndex = Objects.requireNonNull(randomIndex, "randomIndex");
    }

    public MusicTrack select(List<MusicTrack> tracks) {
        return select(tracks, ignored -> true);
    }

    public MusicTrack select(List<MusicTrack> tracks, Predicate<MusicTrack> candidate) {
        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(candidate, "candidate");
        List<MusicTrack> candidates = tracks.stream()
                .filter(Objects::nonNull)
                .filter(candidate)
                .toList();
        return candidates.isEmpty() ? null : candidates.get(randomIndex.applyAsInt(candidates.size()));
    }
}
