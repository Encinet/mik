package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.social.api.SocialPlatformDescriptor;

import java.util.Objects;

/** Immutable management view of one installed social platform. */
public record SocialPlatformSnapshot(
        SocialPlatformDescriptor descriptor,
        long generation,
        SocialPlatformState state,
        String endpoint,
        String lastError,
        int observedConversations
) {
    public SocialPlatformSnapshot {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (generation < 0) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        state = Objects.requireNonNull(state, "state");
        endpoint = endpoint == null ? "" : endpoint;
        lastError = lastError == null ? "" : lastError;
        observedConversations = Math.max(0, observedConversations);
    }
}
