package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicTrackPoolTest {

    @Test
    void mergesCurrentLocalAndCachedOnlineTracksWithoutDuplicateIds() {
        MusicTrack local = local("local.mp3");
        MusicTrack duplicateLocal = local("local.mp3");
        MusicTrack online = online("lx:kw:1");
        AtomicReference<List<MusicTrack>> locals = new AtomicReference<>(List.of(local));
        AtomicReference<List<MusicTrack>> cached = new AtomicReference<>(
                List.of(duplicateLocal, online));
        MusicTrackPool pool = new MusicTrackPool(locals::get, cached::get);

        assertEquals(List.of(local, online), pool.tracks());

        locals.set(List.of());
        cached.set(List.of(online));
        assertEquals(List.of(online), pool.tracks());
    }

    private static MusicTrack local(String id) {
        return new MusicTrack(id,
                new TrackDetails("Local", null, null, "MP3", AudioProperties.EMPTY),
                new TrackTarget.LocalFile(Path.of(id)));
    }

    private static MusicTrack online(String id) {
        return new MusicTrack(id,
                new TrackDetails("Online", null, null, "LX/KW", AudioProperties.EMPTY),
                new TrackTarget.Lx("kw", "1", List.of("320k"),
                        "{\"source\":\"kw\",\"meta\":{\"songId\":\"1\"}}"));
    }
}
