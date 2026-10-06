package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.module.music.catalog.MusicTrack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Two-line title and artist labels for sixteen-track spatial pages. */
final class MusicTrackListLabel {
    private static final int ROW_WIDTH_PIXELS = 160;
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private MusicTrackListLabel() { }

    static Component render(MusicTrack track, int rank) {
        String prefix = "%02d · ".formatted(rank);
        String artist = track.details().artist() != null
                ? track.details().artist() : track.details().originalAuthor();
        return Component.text(fit(track.details().title(), ROW_WIDTH_PIXELS),
                        NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text(prefix, NamedTextColor.GRAY))
                .append(Component.text(artist == null ? "—"
                                : fit(artist, ROW_WIDTH_PIXELS - width(prefix)),
                        NamedTextColor.GRAY));
    }

    static String fit(String raw, int maximumPixels) {
        String text = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        if (text.isEmpty()) return "♪";
        if (width(text) <= maximumPixels) return text;
        StringBuilder result = new StringBuilder();
        int remaining = Math.max(0, maximumPixels - width("…"));
        Matcher matcher = GRAPHEME.matcher(text);
        while (matcher.find()) {
            String grapheme = matcher.group();
            int glyphWidth = width(grapheme);
            if (glyphWidth > remaining) break;
            result.append(grapheme);
            remaining -= glyphWidth;
        }
        return result.toString().stripTrailing() + "…";
    }

    private static int width(String text) {
        int width = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) continue;
            if (codePoint == ' ') width += 4;
            else if (codePoint < 128 && "il.,:;!|'`".indexOf(codePoint) >= 0) width += 2;
            else if (codePoint < 128 && "[](){}tfrI".indexOf(codePoint) >= 0) width += 4;
            else if (codePoint < 128 && "mwMW@#%&".indexOf(codePoint) >= 0) width += 7;
            else width += codePoint < 128 ? 6 : 9;
        }
        return width;
    }
}
