package org.encinet.mik.feature.community.communication;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record Announcement(long timestamp, String content) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    String date() {
        return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).format(DATE);
    }
}
