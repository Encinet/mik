package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicTrackListLabelTest {
    @Test
    void fittingPreservesGraphemeBoundariesAndNormalizesWhitespace() {
        assertEquals("曲".repeat(7) + "…",
                MusicTrackListLabel.fit("曲".repeat(20), 72));
        assertEquals("🎵".repeat(7) + "…",
                MusicTrackListLabel.fit("🎵".repeat(20), 72));
        assertEquals("A B", MusicTrackListLabel.fit("A\nB", 72));
    }

    @Test
    void twoLineRowGivesTheTitleItsWidthAndShowsArtistOrOriginalAuthor() {
        MusicTrack song = track("曲".repeat(20), "歌手", "原作者");
        String row = PlainTextComponentSerializer.plainText()
                .serialize(MusicTrackListLabel.render(song, 7));

        assertEquals("曲".repeat(16) + "…\n07 · 歌手", row);
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(MusicTrackListLabel.render(track("Song", null, "Composer"), 2))
                .endsWith("02 · Composer"));
        assertTrue(PlainTextComponentSerializer.plainText()
                .serialize(MusicTrackListLabel.render(track("Song", null, null), 2))
                .endsWith("02 · —"));
    }

    private static MusicTrack track(String title, String artist, String originalAuthor) {
        return new MusicTrack("sample",
                new TrackDetails(title, artist, originalAuthor,
                        null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of("/tmp/sample.mp3")));
    }
}
