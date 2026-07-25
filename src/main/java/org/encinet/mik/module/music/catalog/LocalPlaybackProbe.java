package org.encinet.mik.module.music.catalog;

import java.nio.file.Path;

/** Checks playback support and returns metadata discovered during the same probe. */
@FunctionalInterface
interface LocalPlaybackProbe {

    PlaybackProbeResult probe(Path path, String extension);
}
