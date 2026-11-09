package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.playback.*;
import org.encinet.mik.feature.entertainment.music.storage.StoredTrack;

/** Visible pending sections retain occurrence identities; shuffle is a candidate list, not a prediction. */
final class MusicQueueRows {
    enum Section { REQUESTS, CONTINUATION }
    record Row(QueueEntry entry, Section section) { }
    static List<Row> of(PlaybackProgram program) {
        var rows = new ArrayList<Row>();
        program.priority().forEach(entry -> rows.add(new Row(entry, Section.REQUESTS)));
        program.pendingBase().forEach(entry -> rows.add(new Row(entry, Section.CONTINUATION)));
        return List.copyOf(rows);
    }
    /** Save what this queue contains: current song and pending entries, in displayed order. */
    static List<MusicTrack> saveTracks(PlaybackProgram program, Map<String, StoredTrack> tracks) {
        var result = new LinkedHashMap<String, MusicTrack>();
        if (program.current() != null) add(result, tracks.get(program.current().trackId()));
        for (var row : of(program)) add(result, tracks.get(row.entry().trackId()));
        return List.copyOf(result.values());
    }
    private static void add(Map<String, MusicTrack> result, StoredTrack track) {
        if (track != null) result.putIfAbsent(track.id(), track.track());
    }
    private MusicQueueRows() { }
}
