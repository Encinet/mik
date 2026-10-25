package org.encinet.mik.feature.entertainment.music.catalog;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/** Owns the ordered metadata-reading chain for ordinary local audio files. */
final class LocalTrackMetadataReader {

    private final TrackMetadataProbe embeddedMetadata;
    private final LocalPlaybackProbe playbackProbe;
    private final TrackMetadataProbe technicalMetadata;
    private final MetadataFallbackPolicy fallbackPolicy;

    LocalTrackMetadataReader() {
        this(new JaudiotaggerMetadataReader(), new LavaplayerMetadataReader(),
                new AudioPropertiesReader(), new MetadataFallbackPolicy());
    }

    LocalTrackMetadataReader(TrackMetadataProbe embeddedMetadata,
                             LocalPlaybackProbe playbackProbe,
                             TrackMetadataProbe technicalMetadata,
                             MetadataFallbackPolicy fallbackPolicy) {
        this.embeddedMetadata = Objects.requireNonNull(embeddedMetadata, "embeddedMetadata");
        this.playbackProbe = Objects.requireNonNull(playbackProbe, "playbackProbe");
        this.technicalMetadata = Objects.requireNonNull(technicalMetadata, "technicalMetadata");
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
    }

    TrackDetails read(Path path, String extension, String fallbackTitle) {
        checkInterrupted();
        LocalTrackMetadata embedded = safeRead(embeddedMetadata, path, extension);
        checkInterrupted();
        PlaybackProbeResult playback = safeProbe(path, extension);
        checkInterrupted();
        if (!playback.playable()) {
            return null;
        }
        LocalTrackMetadata technical = safeRead(technicalMetadata, path, extension);
        checkInterrupted();
        return fallbackPolicy.details(fallbackTitle, extension.toUpperCase(java.util.Locale.ROOT),
                embedded, playback.metadata(), technical);
    }

    private static LocalTrackMetadata safeRead(
            TrackMetadataProbe reader, Path path, String extension) {
        try {
            LocalTrackMetadata result = reader.read(path, extension);
            return result == null ? LocalTrackMetadata.EMPTY : result;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException ignored) {
            return LocalTrackMetadata.EMPTY;
        }
    }

    private PlaybackProbeResult safeProbe(Path path, String extension) {
        try {
            PlaybackProbeResult result = playbackProbe.probe(path, extension);
            return result == null ? PlaybackProbeResult.UNSUPPORTED : result;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException ignored) {
            return PlaybackProbeResult.UNSUPPORTED;
        }
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Local music metadata load interrupted");
        }
    }
}
