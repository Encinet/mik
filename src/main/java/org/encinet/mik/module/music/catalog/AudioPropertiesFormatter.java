package org.encinet.mik.module.music.catalog;

import java.time.Duration;
import java.util.Locale;

/** Formats structured audio properties for player-facing text. */
public final class AudioPropertiesFormatter {

    private AudioPropertiesFormatter() {
    }

    public static String fileSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            return null;
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static String sampleRate(Integer sampleRateHz) {
        if (sampleRateHz == null || sampleRateHz <= 0) {
            return null;
        }
        return sampleRateHz >= 1000
                ? String.format(Locale.ROOT, "%.1f kHz", sampleRateHz / 1000.0)
                : sampleRateHz + " Hz";
    }

    public static String duration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return null;
        }
        long seconds = wholeSeconds(duration);
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainingSeconds = seconds % 60;
        return hours > 0
                ? "%d:%02d:%02d".formatted(hours, minutes, remainingSeconds)
                : "%d:%02d".formatted(minutes, remainingSeconds);
    }

    private static long wholeSeconds(Duration duration) {
        long seconds = duration.getSeconds();
        return duration.getNano() == 0 || seconds == Long.MAX_VALUE ? seconds : seconds + 1;
    }
}
