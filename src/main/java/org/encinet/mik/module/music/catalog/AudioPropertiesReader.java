package org.encinet.mik.module.music.catalog;

import java.nio.file.Path;
import java.util.Objects;

/** Lightweight container parser used when richer metadata does not expose a technical field. */
final class AudioPropertiesReader implements TrackMetadataProbe {

    private final AudioContainerPropertiesParser parser;

    AudioPropertiesReader() {
        this(new AudioContainerPropertiesParser());
    }

    AudioPropertiesReader(AudioContainerPropertiesParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public LocalTrackMetadata read(Path path, String extension) {
        return new LocalTrackMetadata(null, null, null,
                new AudioProperties(parser.getFileSize(path),
                        parser.getSampleRate(path, extension),
                        parser.getDuration(path, extension)));
    }
}
