package org.encinet.mik.feature.entertainment.music.catalog;

import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MusicTrackSelectorTest {

    @Test
    void selectsOnlyFromEligibleTracksWithoutRetryLimit() {
        List<MusicTrack> tracks = List.of(
                track("queued-1"), track("queued-2"), track("available"));
        MusicTrackSelector selector = new MusicTrackSelector(_ -> 0);

        MusicTrack selected = selector.select(tracks,
                track -> track.id().equals(MusicTestTracks.id("available")));

        assertEquals(MusicTestTracks.id("available"), selected.id());
    }

    @Test
    void returnsNullWhenNoCandidateExists() {
        MusicTrackSelector selector = new MusicTrackSelector(_ -> 0);

        assertNull(selector.select(List.of(track("queued")), _ -> false));
        assertNull(selector.select(List.of()));
    }

    @Test
    void appliesRandomIndexAfterFiltering() {
        MusicTrackSelector selector = new MusicTrackSelector(bound -> bound - 1);

        MusicTrack selected = selector.select(
                List.of(track("excluded"), track("first"), track("second")),
                track -> !track.id().equals(MusicTestTracks.id("excluded")));

        assertEquals(MusicTestTracks.id("second"), selected.id());
    }

    private static MusicTrack track(String id) {
        return MusicTestTracks.managed(id, new TrackDetails(id, null, null, null));
    }
}
