package org.encinet.mik.module.music.lyrics;

/** Raw original, translated, and romanized lyric text before timeline parsing. */
record LyricSourceText(String original, String translation, String romanization) {

    LyricSourceText {
        original = normalize(original);
        translation = normalize(translation);
        romanization = normalize(romanization);
    }

    boolean isEmpty() {
        return original == null && translation == null && romanization == null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n")
                .replace('\r', '\n').replace('\0', ' ').strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
