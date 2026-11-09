package org.encinet.mik.feature.entertainment.music.ui;

import java.util.Locale;

/** The player and precise-seek editor use the same clock notation. */
final class MusicTime {
    private MusicTime() { }
    static String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    static long parse(String value) {
        String[] parts = value.strip().split(":", -1);
        if (parts.length > 3 || parts.length == 0) throw new IllegalArgumentException("Invalid time");
        long seconds = 0;
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("[0-9]+")) throw new IllegalArgumentException("Invalid time");
            long part = Long.parseLong(parts[i]);
            if (i > 0 && part >= 60) throw new IllegalArgumentException("Invalid time segment");
            seconds = Math.addExact(Math.multiplyExact(seconds, 60), part);
        }
        return Math.multiplyExact(seconds, 1000);
    }
}
