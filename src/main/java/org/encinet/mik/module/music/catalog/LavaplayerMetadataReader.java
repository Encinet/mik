package org.encinet.mik.module.music.catalog;

import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.nio.file.Path;
import java.time.Duration;

/** Uses Lavaplayer's local container probes to supplement formats without a tag reader. */
final class LavaplayerMetadataReader implements LocalPlaybackProbe {

    private static final String UNKNOWN_TITLE = "Unknown title";
    private static final String UNKNOWN_ARTIST = "Unknown artist";

    private final LocalAudioSourceManager sourceManager = new LocalAudioSourceManager();

    @Override
    public PlaybackProbeResult probe(Path path, String extension) {
        try {
            AudioItem item = sourceManager.loadItem(null,
                    new AudioReference(path.toAbsolutePath().normalize().toString(), null));
            if (!(item instanceof AudioTrack track)) {
                return PlaybackProbeResult.UNSUPPORTED;
            }
            AudioTrackInfo info = track.getInfo();
            return new PlaybackProbeResult(true,
                    new LocalTrackMetadata(knownValue(info.title, UNKNOWN_TITLE),
                            knownValue(info.author, UNKNOWN_ARTIST), null,
                            new AudioProperties(null, null, duration(info.length))));
        } catch (RuntimeException ignored) {
            return PlaybackProbeResult.UNSUPPORTED;
        }
    }

    private static String knownValue(String value, String placeholder) {
        return value == null || value.isBlank() || placeholder.equalsIgnoreCase(value.strip())
                ? null : value;
    }

    private static Duration duration(long milliseconds) {
        return milliseconds <= 0 || milliseconds == Long.MAX_VALUE
                ? null : Duration.ofMillis(milliseconds);
    }
}
