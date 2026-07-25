package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicTrackTest {

    @Test
    void searchesStructuredMetadataWithoutACombinedDisplayName() {
        MusicTrack track = new MusicTrack("album/song.mp3",
                new TrackDetails("Song", "Artist", "Collection", "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of("song.mp3")));

        assertTrue(track.matches("song"));
        assertTrue(track.matches("artist"));
        assertTrue(track.matches("collection"));
        assertTrue(track.matches("album/song"));
        assertFalse(track.matches("missing"));
    }

    @Test
    void rejectsInvalidStructuredMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackDetails(" ", null, null, "MP3", AudioProperties.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> new AudioProperties(-1L, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AudioProperties(null, 0, null));
    }
}
