package org.encinet.mik.module.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class AnnouncementReading {
    static final int LINE_WIDTH_PIXELS = 320;
    static final int LINES_PER_PAGE = 14;
    static final Pattern GLYPHS = Pattern.compile("\\X");

    private AnnouncementReading() { }

    static List<String> pages(String content) {
        List<String> lines = lines(content);
        List<String> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += LINES_PER_PAGE) {
            pages.add(String.join("\n", lines.subList(start,
                    Math.min(start + LINES_PER_PAGE, lines.size()))));
        }
        return List.copyOf(pages);
    }

    static List<String> lines(String content) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : AnnouncementCatalog.normalizeText(content).split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            int start = 0;
            while (start < paragraph.length()) {
                int width = 0;
                int cut = start;
                int space = -1;
                var glyphs = GLYPHS.matcher(paragraph).region(start, paragraph.length());
                while (glyphs.find()) {
                    int nextWidth = textWidth(glyphs.group());
                    if (cut > start && width + nextWidth > LINE_WIDTH_PIXELS) break;
                    width += nextWidth;
                    cut = glyphs.end();
                    if (paragraph.charAt(glyphs.start()) == ' ') space = cut;
                }
                if (cut == paragraph.length()) {
                    lines.add(paragraph.substring(start));
                    break;
                }
                int minimumWordBreak = start + (cut - start) / 2;
                if (space > minimumWordBreak) cut = space;
                lines.add(paragraph.substring(start, cut));
                start = cut;
            }
        }
        return List.copyOf(lines.isEmpty() ? List.of("") : lines);
    }

    static int textWidth(String text) {
        return text.codePoints().map(AnnouncementReading::glyphWidth).sum();
    }

    static int glyphWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK || type == Character.FORMAT
                || codePoint >= 0x1F3FB && codePoint <= 0x1F3FF) return 0;
        if (codePoint == ' ') return 4;
        if (codePoint == '\t') return 8;
        if (codePoint < 128) {
            if ("il.,:;!|'`".indexOf(codePoint) >= 0) return 2;
            if ("[](){}tfrI".indexOf(codePoint) >= 0) return 4;
            if ("mwMW@#%&".indexOf(codePoint) >= 0) return 7;
            return 6;
        }
        return 9;
    }
}
