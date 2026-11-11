package org.encinet.mik.feature.community.social.platform.matrix.sync;

import java.util.Objects;
import java.util.Optional;

/** The cursor and completeness requested for one /sync response. Tokens remain opaque. */
public record MatrixSyncRequest(Optional<String> since, long timeoutMillis, boolean fullState) {
    public MatrixSyncRequest {
        since = Objects.requireNonNull(since, "since");
        if (since.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("Matrix sync cursor must not be blank");
        }
        if (timeoutMillis < 0 || timeoutMillis > 60_000) {
            throw new IllegalArgumentException("Matrix sync timeout must be between 0 and 60000 ms");
        }
    }

    public boolean completeState() {
        return fullState || since.isEmpty();
    }
}
