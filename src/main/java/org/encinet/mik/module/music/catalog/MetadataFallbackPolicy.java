package org.encinet.mik.module.music.catalog;

/** Combines partial metadata while keeping each field's fallback independent. */
final class MetadataFallbackPolicy {

    private static final int MAX_TEXT_LENGTH = 512;

    TrackDetails details(String fallbackTitle, String format,
                         LocalTrackMetadata embedded,
                         LocalTrackMetadata probed,
                         LocalTrackMetadata technical) {
        return new TrackDetails(
                required(firstText(embedded.title(), probed.title(), fallbackTitle), fallbackTitle),
                firstText(embedded.artist(), probed.artist()),
                firstText(embedded.album(), probed.album()),
                required(format, "AUDIO"),
                new AudioProperties(
                        firstValue(embedded.audio().fileSizeBytes(), probed.audio().fileSizeBytes(),
                                technical.audio().fileSizeBytes()),
                        firstValue(embedded.audio().sampleRateHz(), probed.audio().sampleRateHz(),
                                technical.audio().sampleRateHz()),
                        firstValue(embedded.audio().duration(), probed.audio().duration(),
                                technical.audio().duration())));
    }

    private static String required(String value, String fallback) {
        String sanitized = sanitize(value);
        if (sanitized != null) {
            return sanitized;
        }
        String fallbackValue = sanitize(fallback);
        return fallbackValue == null ? "Unknown" : fallbackValue;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            String sanitized = sanitize(value);
            if (sanitized != null) {
                return sanitized;
            }
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstValue(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        if (text.isEmpty() || text.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        int end = MAX_TEXT_LENGTH;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
