package org.encinet.mik.feature.community.communication;

import java.util.regex.Pattern;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class AnnouncementReading {
    static final Pattern GLYPHS = Pattern.compile("\\X");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AnnouncementReading() { }

    static String date(long timestamp) {
        return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).format(DATE);
    }

    static String excerpt(String content) {
        String normalized = AnnouncementCatalog.normalizeText(content).strip().replaceAll("\\s+", " ");
        var glyphs = GLYPHS.matcher(normalized);
        int end = 0;
        for (int count = 0; count < 24 && glyphs.find(); count++) end = glyphs.end();
        return end < normalized.length() ? normalized.substring(0, end).stripTrailing() + "…" : normalized;
    }
}
