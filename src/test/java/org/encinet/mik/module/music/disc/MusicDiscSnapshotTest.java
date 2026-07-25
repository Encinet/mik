package org.encinet.mik.module.music.disc;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class MusicDiscSnapshotTest {

    @Test
    void restoresOnlineTrackFromCurrentMikDiscFormat() {
        MusicTrack track = new MusicTrack("lx:kw:7",
                new TrackDetails("Song", "Artist", "Album", "LX/KW",
                        new AudioProperties(null, null, Duration.ofSeconds(190))),
                new TrackTarget.Lx("kw", "7", List.of("128k", "320k"),
                        "{\"name\":\"Song\",\"source\":\"kw\",\"songmid\":\"7\"}",
                        "provider.js"));

        String snapshot = MusicDiscSnapshot.serialize(track);
        MusicTrack restored = MusicDiscSnapshot.deserialize(track.id(), snapshot);

        assertEquals(track.id(), restored.id());
        assertEquals(track.details(), restored.details());
        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                restored.target());
        assertEquals("kw", target.source());
        assertEquals("7", target.songId());
        assertEquals(List.of("128k", "320k"), target.qualities());
        assertEquals("provider.js", target.providerId());
    }

    @Test
    void rejectsSnapshotWhoseTrackIdDoesNotMatchDisc() {
        MusicTrack track = new MusicTrack("lx:kw:7",
                new TrackDetails("Song", null, null, "LX/KW", AudioProperties.EMPTY),
                new TrackTarget.Lx("kw", "7", List.of("320k"),
                        "{\"name\":\"Song\",\"source\":\"kw\",\"songmid\":\"7\"}"));

        assertNull(MusicDiscSnapshot.deserialize("lx:kw:other",
                MusicDiscSnapshot.serialize(track)));
    }

    @Test
    void doesNotCreateSnapshotForLocalTrack() {
        MusicTrack track = new MusicTrack("local:test",
                new TrackDetails("Local", null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(java.nio.file.Path.of("test.mp3")));

        assertNull(MusicDiscSnapshot.serialize(track));
    }

    @Test
    void limitsRestoredQualitiesAndPreservesTheirOrder() {
        StringBuilder qualities = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            if (index > 0) {
                qualities.append(',');
            }
            qualities.append('"').append("q").append(index).append('"');
        }
        String snapshot = """
                {"id":"lx:kw:many","title":"Many","format":"LX/KW","source":"kw",
                 "songId":"many","qualities":[%s],"musicInfo":{"id":"many"}}
                """.formatted(qualities);

        MusicTrack restored = MusicDiscSnapshot.deserialize("lx:kw:many", snapshot);

        TrackTarget.Lx target = assertInstanceOf(TrackTarget.Lx.class,
                restored.target());
        assertEquals(64, target.qualities().size());
        assertEquals("q0", target.qualities().getFirst());
        assertEquals("q63", target.qualities().getLast());
    }

    @Test
    void rejectsNonObjectOrMismatchedMusicInfo() {
        assertNull(MusicDiscSnapshot.deserialize("lx:kw:7", """
                {"id":"lx:kw:7","title":"Song","format":"LX/KW","source":"kw",
                 "songId":"7","qualities":[],"musicInfo":[]}
                """));
        assertNull(MusicDiscSnapshot.deserialize("lx:kw:7", """
                {"id":"lx:kw:7","title":"Song","format":"LX/KW","source":"kw",
                 "songId":"7","qualities":[],"musicInfo":{"source":"wy"}}
                """));
    }

    @Test
    void serializerDoesNotEmitSnapshotsRejectedForControlCharacters() {
        MusicTrack track = new MusicTrack("lx:kw:7",
                new TrackDetails("Line\nBreak", null, null, "LX/KW", AudioProperties.EMPTY),
                new TrackTarget.Lx("kw", "7", List.of("320k"),
                        "{\"source\":\"kw\",\"songmid\":\"7\"}"));

        assertNull(MusicDiscSnapshot.serialize(track));
    }

    @Test
    void rejectsNonStringTextAndInvalidPresentProviderId() {
        assertNull(MusicDiscSnapshot.deserialize("lx:kw:7", """
                {"id":"lx:kw:7","title":7,"format":"LX/KW","source":"kw",
                 "songId":"7","qualities":[],"musicInfo":{"source":"kw"}}
                """));
        assertNull(MusicDiscSnapshot.deserialize("lx:kw:7", """
                {"id":"lx:kw:7","title":"Song","format":"LX/KW","source":"kw",
                 "songId":"7","providerId":{},"qualities":[],"musicInfo":{"source":"kw"}}
                """));
        assertNull(MusicDiscSnapshot.deserialize("lx:kw:7", """
                {"id":"lx:kw:7","title":"Song","artist":7,"format":"LX/KW","source":"kw",
                 "songId":"7","qualities":[],"musicInfo":{"source":"kw"}}
                """));
        assertNull(MusicDiscSnapshot.deserialize("lx:kw:7", """
                {"id":"lx:kw:7","title":"Song","durationSeconds":"bad","format":"LX/KW",
                 "source":"kw","songId":"7","qualities":[],"musicInfo":{"source":"kw"}}
                """));
    }
}
