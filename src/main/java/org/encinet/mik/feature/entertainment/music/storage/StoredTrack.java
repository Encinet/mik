package org.encinet.mik.feature.entertainment.music.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.encinet.mik.feature.entertainment.music.catalog.AudioProperties;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;

/** Durable metadata, never a resolved or expiring media URL. */
public record StoredTrack(String id, String title, String artist, String author, String album, String format,
        Long bytes, Integer sampleRate, Long duration, String kind, String path, String root, String source,
        String song, List<String> qualities, String info, String provider) {
    public static StoredTrack of(MusicTrack track) {
        TrackDetails d = track.details();
        AudioProperties a = d.audio();
        String kind = null, path = null, root = null, source = null, song = null, info = null,
               provider = null;
        List<String> qualities = List.of();
        switch (track.target()) {
            case TrackTarget.NativeRecord f -> {
                kind = "record";
                song = f.sound();
            }
            case TrackTarget.LocalFile f -> {
                kind = "audio";
                path = f.path().toString();
                root = f.root().toString();
            }
            case TrackTarget.NbsFile f -> {
                kind = "nbs";
                path = f.path().toString();
                root = f.root().toString();
            }
            case TrackTarget.Lx f -> {
                kind = "lx";
                source = f.source();
                song = f.songId();
                qualities = f.qualities();
                info = f.musicInfoJson();
                provider = f.providerId();
            }
        }
        return new StoredTrack(track.id(), d.title(), d.artist(), d.originalAuthor(), d.album(), d.format(),
                a.fileSizeBytes(), a.sampleRateHz(), a.duration() == null ? null : a.duration().toMillis(),
                kind, path, root, source, song, qualities, info, provider);
    }
    public MusicTrack track() {
        TrackTarget target = switch (kind) {
            case "record" -> new TrackTarget.NativeRecord(song);
            case "audio" -> new TrackTarget.LocalFile(Path.of(path), Path.of(root));
            case "nbs" -> new TrackTarget.NbsFile(Path.of(path), Path.of(root));
            case "lx" -> new TrackTarget.Lx(source, song, qualities, info, provider);
            default -> throw new IllegalArgumentException("Unknown track type");
        };
        return new MusicTrack(id,
                new TrackDetails(title, artist, author, album, format,
                        new AudioProperties(
                                bytes, sampleRate, duration == null ? null : Duration.ofMillis(duration))),
                target);
    }
}
