package org.encinet.mik.feature.community.chat.mention;

/** Shared boundaries for player and broadcast mentions in ordinary chat text. */
public final class MentionBoundaries {
    private MentionBoundaries() {
    }

    public static boolean isStart(String text, int offset) {
        return offset == 0 || !isWordCharacter(text.codePointBefore(offset));
    }

    public static boolean isEnd(String text, int offset) {
        return offset == text.length() || !isWordCharacter(text.codePointAt(offset));
    }

    private static boolean isWordCharacter(int codePoint) {
        // East Asian prose commonly touches handles without an intervening space.
        return switch (Character.UnicodeScript.of(codePoint)) {
            case HAN, HIRAGANA, KATAKANA, HANGUL -> false;
            default -> {
                int type = Character.getType(codePoint);
                yield Character.isLetterOrDigit(codePoint) || codePoint == '_'
                        || type == Character.NON_SPACING_MARK
                        || type == Character.COMBINING_SPACING_MARK;
            }
        };
    }
}
