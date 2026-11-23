package org.encinet.mik.feature.entertainment.music.catalog;

import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicTrackTest {

    @Test
    void searchesStructuredMetadataWithoutACombinedDisplayName() {
        MusicTrack track = MusicTestTracks.managed("album/song.mp3", new TrackDetails("Song", "Artist", "Original Author", "Collection", null));

        assertTrue(track.matches("song"));
        assertTrue(track.matches("artist"));
        assertTrue(track.matches("original author"));
        assertTrue(track.matches("collection"));
        assertTrue(track.matches(track.id()));
        assertFalse(track.matches("missing"));
    }

    @Test
    void rejectsInvalidStructuredMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackDetails(" ", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackDetails("Track", null, null, Duration.ofSeconds(-1)));
    }
}
