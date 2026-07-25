package org.encinet.mik.module.music.lyrics;

/** One lyric event on the playback timeline; blank text clears the display. */
public record LyricLine(long timestampMillis, String text,
                        String translation, String romanization) {

    public LyricLine {
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("timestampMillis must not be negative");
        }
        text = normalizeText(text);
        translation = normalize(translation);
        romanization = normalize(romanization);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            throw new IllegalArgumentException("lyric text must not be null");
        }
        return value.replace('\0', ' ').strip();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\0', ' ').strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
