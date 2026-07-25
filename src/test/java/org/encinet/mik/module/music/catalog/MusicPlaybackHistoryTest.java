package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPlaybackHistoryTest {

    @TempDir
    Path directory;

    @Test
    void persistsSuccessfulPlaybackCountsAcrossRestarts() {
        Path file = directory.resolve("state/music-playback.json");
        Instant playedAt = Instant.parse("2026-07-26T04:00:00Z");
        MusicTrack track = localTrack("music/song.mp3");
        MusicPlaybackHistory history = history(file, playedAt, new ArrayList<>());

        history.recordPlayback(track);
        history.recordPlayback(track);
        history.close();

        MusicPlaybackHistory restored = history(file,
                playedAt.plusSeconds(60), new ArrayList<>());
        assertEquals(new MusicPlaybackStats.TrackStats(2, playedAt), restored.stats(track));
        restored.close();
    }

    @Test
    void ignoresMalformedHistoryWithoutPreventingFutureSaves() throws Exception {
        Path file = directory.resolve("state/music-playback.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{not-json");
        List<String> warnings = new ArrayList<>();
        MusicTrack track = localTrack("music/recovered.mp3");
        MusicPlaybackHistory history = history(file,
                Instant.parse("2026-07-26T05:00:00Z"), warnings);

        assertEquals(MusicPlaybackStats.TrackStats.EMPTY, history.stats(track));
        history.recordPlayback(track);
        history.close();

        assertTrue(warnings.stream().anyMatch(value -> value.contains("Failed to load")));
        MusicPlaybackHistory restored = history(file,
                Instant.parse("2026-07-26T06:00:00Z"), new ArrayList<>());
        assertEquals(1, restored.stats(track).playCount());
        restored.close();
    }

    private static MusicPlaybackHistory history(Path file, Instant now, List<String> warnings) {
        return new MusicPlaybackHistory(file, warnings::add,
                Clock.fixed(now, ZoneOffset.UTC), Executors.newSingleThreadExecutor());
    }

    private static MusicTrack localTrack(String id) {
        return new MusicTrack(id,
                new TrackDetails("Song", "Artist", null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id)));
    }
}
