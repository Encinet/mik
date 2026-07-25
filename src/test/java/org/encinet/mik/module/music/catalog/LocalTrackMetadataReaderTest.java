package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalTrackMetadataReaderTest {

    private static final Path TRACK = Path.of("song.webm");

    @Test
    void mergesEveryFieldIndependentlyInPriorityOrder() {
        LocalTrackMetadataReader reader = new LocalTrackMetadataReader(
                (path, extension) -> new LocalTrackMetadata(
                        "Embedded title", null, "Embedded album",
                        new AudioProperties(null, 48_000, null)),
                (path, extension) -> new PlaybackProbeResult(true,
                        new LocalTrackMetadata("Probe title", "Probe artist", null,
                                new AudioProperties(null, null, Duration.ofSeconds(61)))),
                (path, extension) -> new LocalTrackMetadata(null, null, null,
                        new AudioProperties(4096L, 44_100, Duration.ofSeconds(90))),
                new MetadataFallbackPolicy());

        TrackDetails details = reader.read(TRACK, "webm", "File name");

        assertEquals("Embedded title", details.title());
        assertEquals("Probe artist", details.artist());
        assertEquals("Embedded album", details.album());
        assertEquals("WEBM", details.format());
        assertEquals(new AudioProperties(4096L, 48_000, Duration.ofSeconds(61)),
                details.audio());
    }

    @Test
    void fallsBackToFileNameWhenMetadataIsMissingOrUnsafe() {
        LocalTrackMetadataReader reader = new LocalTrackMetadataReader(
                (path, extension) -> new LocalTrackMetadata(
                        "bad\nvalue", " ", null, AudioProperties.EMPTY),
                (path, extension) -> new PlaybackProbeResult(true, LocalTrackMetadata.EMPTY),
                (path, extension) -> LocalTrackMetadata.EMPTY,
                new MetadataFallbackPolicy());

        TrackDetails details = reader.read(TRACK, "webm", "Readable file name");

        assertEquals("Readable file name", details.title());
        assertNull(details.artist());
        assertNull(details.album());
    }

    @Test
    void isolatesReaderFailuresButRequiresPlaybackSupport() {
        LocalTrackMetadataReader recoverable = new LocalTrackMetadataReader(
                (path, extension) -> { throw new IllegalStateException("bad tag"); },
                (path, extension) -> new PlaybackProbeResult(true, LocalTrackMetadata.EMPTY),
                (path, extension) -> { throw new IllegalStateException("bad header"); },
                new MetadataFallbackPolicy());

        assertEquals("Fallback", recoverable.read(TRACK, "webm", "Fallback").title());

        LocalTrackMetadataReader unsupported = new LocalTrackMetadataReader(
                (path, extension) -> new LocalTrackMetadata(
                        "Tagged", "Artist", "Album", AudioProperties.EMPTY),
                (path, extension) -> PlaybackProbeResult.UNSUPPORTED,
                (path, extension) -> LocalTrackMetadata.EMPTY,
                new MetadataFallbackPolicy());

        assertNull(unsupported.read(TRACK, "webm", "Fallback"));
    }
}
