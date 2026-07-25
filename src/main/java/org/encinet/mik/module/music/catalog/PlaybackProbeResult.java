package org.encinet.mik.module.music.catalog;

/** Result of probing whether a local file is supported by the active playback engine. */
record PlaybackProbeResult(boolean playable, LocalTrackMetadata metadata) {

    static final PlaybackProbeResult UNSUPPORTED = new PlaybackProbeResult(
            false, LocalTrackMetadata.EMPTY);

    PlaybackProbeResult {
        metadata = metadata == null ? LocalTrackMetadata.EMPTY : metadata;
    }
}
