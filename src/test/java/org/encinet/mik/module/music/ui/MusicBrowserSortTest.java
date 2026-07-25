package org.encinet.mik.module.music.ui;

import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicBrowserSortTest {

    @Test
    void mostPlayedAndRecentModesUseServerPlaybackStats() {
        MusicTrack alpha = track("alpha.mp3");
        MusicTrack bravo = track("bravo.mp3");
        MusicTrack charlie = track("charlie.mp3");
        Map<String, MusicPlaybackStats.TrackStats> values = Map.of(
                alpha.id(), stats(5, "2026-07-20T00:00:00Z"),
                bravo.id(), stats(2, "2026-07-25T00:00:00Z"));
        MusicPlaybackStats stats = id -> values.getOrDefault(
                id, MusicPlaybackStats.TrackStats.EMPTY);
        List<MusicTrack> source = List.of(charlie, alpha, bravo);

        assertEquals(List.of(alpha, bravo, charlie),
                MusicBrowserSort.MOST_PLAYED.order(source, stats));
        assertEquals(List.of(bravo, alpha, charlie),
                MusicBrowserSort.RECENTLY_PLAYED.order(source, stats));
        assertEquals(source, MusicBrowserSort.DEFAULT.order(source, stats));
    }

    @Test
    void sortControlCyclesThroughEveryMode() {
        assertEquals(MusicBrowserSort.MOST_PLAYED, MusicBrowserSort.DEFAULT.next());
        assertEquals(MusicBrowserSort.RECENTLY_PLAYED, MusicBrowserSort.MOST_PLAYED.next());
        assertEquals(MusicBrowserSort.DEFAULT, MusicBrowserSort.RECENTLY_PLAYED.next());
    }

    private static MusicPlaybackStats.TrackStats stats(long count, String lastPlayedAt) {
        return new MusicPlaybackStats.TrackStats(count, Instant.parse(lastPlayedAt));
    }

    private static MusicTrack track(String id) {
        return new MusicTrack(id,
                new TrackDetails(id, null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id)));
    }
}
