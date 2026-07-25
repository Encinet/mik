package org.encinet.mik.module.music.catalog;

import java.time.Duration;

/** Numeric audio properties kept independent from their presentation. */
public record AudioProperties(
        Long fileSizeBytes,
        Integer sampleRateHz,
        Duration duration
) {

    public static final AudioProperties EMPTY = new AudioProperties(null, null, null);

    public AudioProperties {
        if (fileSizeBytes != null && fileSizeBytes < 0) {
            throw new IllegalArgumentException("fileSizeBytes must not be negative");
        }
        if (sampleRateHz != null && sampleRateHz <= 0) {
            throw new IllegalArgumentException("sampleRateHz must be positive");
        }
        if (duration != null && (duration.isNegative() || duration.isZero())) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }
}
