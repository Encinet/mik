package org.encinet.mik.module.music.disc;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.OnlineTrackSnapshot;

/** Disc-format facade for the shared validated online-track snapshot. */
public final class MusicDiscSnapshot {

    private MusicDiscSnapshot() {
    }

    public static String serialize(MusicTrack track) {
        return OnlineTrackSnapshot.serialize(track);
    }

    public static MusicTrack deserialize(String expectedTrackId, String serialized) {
        return OnlineTrackSnapshot.deserialize(expectedTrackId, serialized);
    }
}
