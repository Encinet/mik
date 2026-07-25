package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MusicTrackSelectorTest {

    @Test
    void selectsOnlyFromEligibleTracksWithoutRetryLimit() {
        List<MusicTrack> tracks = List.of(
                track("queued-1"), track("queued-2"), track("available"));
        MusicTrackSelector selector = new MusicTrackSelector(bound -> 0);

        MusicTrack selected = selector.select(tracks,
                track -> track.id().equals("available"));

        assertEquals("available", selected.id());
    }

    @Test
    void returnsNullWhenNoCandidateExists() {
        MusicTrackSelector selector = new MusicTrackSelector(bound -> 0);

        assertNull(selector.select(List.of(track("queued")), ignored -> false));
        assertNull(selector.select(List.of()));
    }

    @Test
    void appliesRandomIndexAfterFiltering() {
        MusicTrackSelector selector = new MusicTrackSelector(bound -> bound - 1);

        MusicTrack selected = selector.select(
                List.of(track("excluded"), track("first"), track("second")),
                track -> !track.id().equals("excluded"));

        assertEquals("second", selected.id());
    }

    private static MusicTrack track(String id) {
        return new MusicTrack(id, new TrackDetails(id, null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id + ".mp3")));
    }
}
