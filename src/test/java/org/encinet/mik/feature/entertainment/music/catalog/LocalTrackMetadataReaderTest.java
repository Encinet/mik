package org.encinet.mik.feature.entertainment.music.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTrackMetadataReaderTest {

    private static final Path TRACK = Path.of("song.webm");

    @Test
    void mergesEveryFieldIndependentlyInPriorityOrder() {
        LocalTrackMetadataReader reader = new LocalTrackMetadataReader(
                (_, _) -> new LocalTrackMetadata(
                        "Embedded title", null, "Embedded album",
                        new AudioProperties(null, 48_000, null)),
                (_, _) -> new PlaybackProbeResult(true,
                        new LocalTrackMetadata("Probe title", "Probe artist", null,
                                new AudioProperties(null, null, Duration.ofSeconds(61)))),
                (_, _) -> new LocalTrackMetadata(null, null, null,
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
                (_, _) -> new LocalTrackMetadata(
                        "bad\nvalue", " ", null, AudioProperties.EMPTY),
                (_, _) -> new PlaybackProbeResult(true, LocalTrackMetadata.EMPTY),
                (_, _) -> LocalTrackMetadata.EMPTY,
                new MetadataFallbackPolicy());

        TrackDetails details = reader.read(TRACK, "webm", "Readable file name");

        assertEquals("Readable file name", details.title());
        assertNull(details.artist());
        assertNull(details.album());
    }

    @Test
    void stopsBeforeProbingWhenTagReadingIsInterrupted() {
        AtomicInteger probes = new AtomicInteger();
        LocalTrackMetadataReader reader = new LocalTrackMetadataReader(
                (_, _) -> {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted tag reader");
                },
                (_, _) -> {
                    probes.incrementAndGet();
                    return PlaybackProbeResult.UNSUPPORTED;
                },
                (_, _) -> {
                    probes.incrementAndGet();
                    return LocalTrackMetadata.EMPTY;
                },
                new MetadataFallbackPolicy());

        try {
            assertThrows(CancellationException.class, () -> reader.read(TRACK, "webm", "Fallback"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, probes.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void doesNotTreatCancelledPlaybackProbeAsAnUnsupportedFile() {
        CancellationException cancelled = new CancellationException("Stopped");
        LocalTrackMetadataReader reader = new LocalTrackMetadataReader(
                (_, _) -> LocalTrackMetadata.EMPTY,
                (_, _) -> { throw cancelled; },
                (_, _) -> { throw new AssertionError("Must not read after cancellation"); },
                new MetadataFallbackPolicy());

        assertSame(cancelled, assertThrows(CancellationException.class,
                () -> reader.read(TRACK, "webm", "Fallback")));
    }

    @Test
    void isolatesReaderFailuresButRequiresPlaybackSupport() {
        LocalTrackMetadataReader recoverable = new LocalTrackMetadataReader(
                (_, _) -> { throw new IllegalStateException("bad tag"); },
                (_, _) -> new PlaybackProbeResult(true, LocalTrackMetadata.EMPTY),
                (_, _) -> { throw new IllegalStateException("bad header"); },
                new MetadataFallbackPolicy());

        assertEquals("Fallback", recoverable.read(TRACK, "webm", "Fallback").title());

        LocalTrackMetadataReader unsupported = new LocalTrackMetadataReader(
                (_, _) -> new LocalTrackMetadata(
                        "Tagged", "Artist", "Album", AudioProperties.EMPTY),
                (_, _) -> PlaybackProbeResult.UNSUPPORTED,
                (_, _) -> LocalTrackMetadata.EMPTY,
                new MetadataFallbackPolicy());

        assertNull(unsupported.read(TRACK, "webm", "Fallback"));
    }
}
