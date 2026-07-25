package org.encinet.mik.module.music.ui;

import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.MusicTrack;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Sort modes exposed by the music browser. */
enum MusicBrowserSort {
    DEFAULT,
    MOST_PLAYED,
    RECENTLY_PLAYED;

    MusicBrowserSort next() {
        MusicBrowserSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    List<MusicTrack> order(List<MusicTrack> tracks, MusicPlaybackStats stats) {
        if (this == DEFAULT) {
            return List.copyOf(tracks);
        }
        Comparator<MusicTrack> comparator = switch (this) {
            case MOST_PLAYED -> Comparator
                    .comparingLong((MusicTrack track) -> stats.stats(track).playCount()).reversed()
                    .thenComparing(track -> stats.stats(track).lastPlayedAt(),
                            Comparator.nullsLast(Comparator.reverseOrder()));
            case RECENTLY_PLAYED -> Comparator
                    .comparing((MusicTrack track) -> stats.stats(track).lastPlayedAt(),
                            Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
                    .thenComparing(Comparator.comparingLong(
                            (MusicTrack track) -> stats.stats(track).playCount()).reversed());
            case DEFAULT -> throw new IllegalStateException("handled above");
        };
        return tracks.stream().sorted(comparator).toList();
    }
}
