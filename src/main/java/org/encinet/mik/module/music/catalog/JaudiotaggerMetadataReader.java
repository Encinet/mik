package org.encinet.mik.module.music.catalog;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/** Reads embedded tags and precise technical metadata from supported audio containers. */
final class JaudiotaggerMetadataReader implements TrackMetadataProbe {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp3", "flac", "ogg", "oga", "m4a", "mp4", "wav"
    );

    @Override
    public LocalTrackMetadata read(Path path, String extension) {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(normalizedExtension)) {
            return LocalTrackMetadata.EMPTY;
        }
        try {
            AudioFile file = AudioFileIO.readAs(path.toFile(),
                    normalizedExtension.equals("oga") ? "ogg" : normalizedExtension);
            AudioHeader header = file.getAudioHeader();
            Tag tag = file.getTag();
            return new LocalTrackMetadata(
                    field(tag, FieldKey.TITLE),
                    field(tag, FieldKey.ARTIST),
                    field(tag, FieldKey.ALBUM),
                    new AudioProperties(null, sampleRate(header), duration(header)));
        } catch (Exception ignored) {
            // A corrupt or unsupported tag must not hide an otherwise playable local file.
            return LocalTrackMetadata.EMPTY;
        }
    }

    private static String field(Tag tag, FieldKey key) {
        if (tag == null) {
            return null;
        }
        try {
            String value = tag.getFirst(key);
            if (value == null) {
                return null;
            }
            String normalized = value.replace('\0', ' ').strip();
            return normalized.isEmpty() ? null : normalized;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Integer sampleRate(AudioHeader header) {
        if (header == null) {
            return null;
        }
        try {
            int sampleRate = header.getSampleRateAsNumber();
            return sampleRate > 0 ? sampleRate : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Duration duration(AudioHeader header) {
        if (header == null) {
            return null;
        }
        try {
            double seconds = header.getPreciseTrackLength();
            if (!Double.isFinite(seconds) || seconds <= 0) {
                return null;
            }
            long milliseconds = Math.round(seconds * 1_000.0);
            return milliseconds > 0 ? Duration.ofMillis(milliseconds) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
