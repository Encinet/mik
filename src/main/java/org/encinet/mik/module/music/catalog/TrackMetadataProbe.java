package org.encinet.mik.module.music.catalog;

import java.nio.file.Path;

/** Reads any metadata it can recover from one ordinary local audio file. */
@FunctionalInterface
interface TrackMetadataProbe {

    LocalTrackMetadata read(Path path, String extension);
}
