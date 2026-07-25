package org.encinet.mik.module.music.catalog;

/** Partial metadata reported by one local-file reader. */
record LocalTrackMetadata(
        String title,
        String artist,
        String album,
        AudioProperties audio
) {

    static final LocalTrackMetadata EMPTY = new LocalTrackMetadata(null, null, null,
            AudioProperties.EMPTY);

    LocalTrackMetadata {
        title = normalize(title);
        artist = normalize(artist);
        album = normalize(album);
        audio = audio == null ? AudioProperties.EMPTY : audio;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
