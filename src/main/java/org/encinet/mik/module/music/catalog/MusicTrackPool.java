package org.encinet.mik.module.music.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Dynamic union of local tracks and fully cached online tracks. */
public final class MusicTrackPool {

    private final Supplier<List<MusicTrack>> localTracks;
    private final Supplier<List<MusicTrack>> cachedOnlineTracks;

    public MusicTrackPool(Supplier<List<MusicTrack>> localTracks,
                          Supplier<List<MusicTrack>> cachedOnlineTracks) {
        this.localTracks = Objects.requireNonNull(localTracks, "localTracks");
        this.cachedOnlineTracks = Objects.requireNonNull(
                cachedOnlineTracks, "cachedOnlineTracks");
    }

    public List<MusicTrack> tracks() {
        Map<String, MusicTrack> merged = new LinkedHashMap<>();
        addAll(merged, localTracks.get());
        addAll(merged, cachedOnlineTracks.get());
        return List.copyOf(merged.values());
    }

    private static void addAll(Map<String, MusicTrack> target, List<MusicTrack> tracks) {
        if (tracks == null) {
            return;
        }
        for (MusicTrack track : tracks) {
            if (track != null) {
                target.putIfAbsent(track.id(), track);
            }
        }
    }
}
