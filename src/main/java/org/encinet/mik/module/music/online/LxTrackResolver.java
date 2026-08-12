package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.TrackTarget;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface LxTrackResolver {

    CompletableFuture<String> resolve(TrackTarget.Lx target);

    /**
     * Resolves one playback candidate while allowing download callers to reject providers that
     * already failed for this operation. Implementations without provider awareness retain the
     * original functional-interface behavior.
     */
    default CompletableFuture<Resolution> resolveCandidate(
            TrackTarget.Lx target, Set<String> excludedProviderIds) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(excludedProviderIds, "excludedProviderIds");
        return resolve(target).thenApply(url -> new Resolution(url, null));
    }

    /** Reports that a resolved candidate produced complete, cacheable audio. */
    default void candidateSucceeded(TrackTarget.Lx target, Resolution resolution) {
    }

    /** Reports that a resolved candidate failed at the remote-audio boundary. */
    default void candidateFailed(TrackTarget.Lx target, Resolution resolution, Throwable error) {
    }

    record Resolution(String url, String providerId) {
        public Resolution {
            url = requireText(url, "url");
            providerId = providerId == null || providerId.isBlank()
                    ? null : requireText(providerId, "providerId");
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.strip();
        }
    }
}
