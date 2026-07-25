package org.encinet.mik.module.music.ui;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicBrowserGuiTest {

    @Test
    void keepsLocalTracksFirstAndStablyPrioritizesCachedOnlineTracks() {
        MusicTrack uncachedFirst = online("lx:kw:1", "1");
        MusicTrack local = local("local.mp3");
        MusicTrack cachedFirst = online("lx:kw:2", "2");
        MusicTrack uncachedSecond = online("lx:kw:3", "3");
        MusicTrack cachedSecond = online("lx:kw:4", "4");
        Set<String> cached = Set.of(cachedFirst.id(), cachedSecond.id());

        List<MusicTrack> ordered = MusicBrowserGui.prioritizeCachedOnline(
                List.of(uncachedFirst, local, cachedFirst, uncachedSecond, cachedSecond),
                track -> cached.contains(track.id()));

        assertEquals(List.of(local, cachedFirst, cachedSecond, uncachedFirst, uncachedSecond),
                ordered);
    }

    private static MusicTrack local(String id) {
        return new MusicTrack(id,
                new TrackDetails(id, null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id)));
    }

    private static MusicTrack online(String id, String songId) {
        return new MusicTrack(id,
                new TrackDetails(id, null, null, "LX/KW", AudioProperties.EMPTY),
                new TrackTarget.Lx("kw", songId, List.of("320k"),
                        "{\"source\":\"kw\",\"meta\":{\"songId\":\""
                                + songId + "\"}}"));
    }
}
