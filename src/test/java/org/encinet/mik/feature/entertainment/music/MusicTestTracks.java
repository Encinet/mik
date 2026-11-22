package org.encinet.mik.feature.entertainment.music;

import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MusicTestTracks {
    private MusicTestTracks() { }

    public static String id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static TrackTarget.Managed target(String name) {
        return new TrackTarget.Managed(id(name));
    }

    public static MusicTrack managed(String name, TrackDetails details) {
        var target = target(name);
        return new MusicTrack(target.trackId(), details, target);
    }
}
