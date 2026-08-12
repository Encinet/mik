package org.encinet.mik.module.social.runtime;

/** Per-platform decisions applied by the shared output pipeline. */
public record SocialOutputPolicy(boolean contentSafetyEnabled, int maximumLength) {
    public SocialOutputPolicy {
        if (maximumLength < 100 || maximumLength > 100_000) {
            throw new IllegalArgumentException("maximumLength is out of range");
        }
    }
}
