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
        int observedConversations,
        long inboundAccepted,
        long inboundBackpressured,
        long outboundAccepted,
        long outboundBackpressured,
        long deliveryFailures
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
        if (inboundAccepted < 0 || inboundBackpressured < 0
                || outboundAccepted < 0 || outboundBackpressured < 0
                || deliveryFailures < 0) {
            throw new IllegalArgumentException("social counters must not be negative");
        }
    }

    public SocialPlatformSnapshot(
            SocialPlatformDescriptor descriptor,
            long generation,
            SocialPlatformState state,
            String endpoint,
            String lastError,
            int observedConversations
    ) {
        this(descriptor, generation, state, endpoint, lastError,
                observedConversations, 0, 0, 0, 0, 0);
    }
}
