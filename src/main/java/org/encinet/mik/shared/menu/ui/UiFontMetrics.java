package org.encinet.mik.shared.menu.ui;

/** Default Minecraft font advances in pixels; non-ASCII glyphs use a nine-pixel estimate. */
public final class UiFontMetrics {
    /** Server-side vertical calibration; resource-pack fonts still require client verification. */
    public static final int ASCENT_ESTIMATE = 7;
    private UiFontMetrics() { }

    public static int width(String text) {
        return text.codePoints().map(UiFontMetrics::advance).sum();
    }

    public static int advance(int point) {
        // 26.3 bitmap font and space provider. Combining marks are separate client glyphs.
        return switch (point) {
            case '\n', '\u200c' -> 0;
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> point < 128 ? 6 : 9;
        };
    }
}
