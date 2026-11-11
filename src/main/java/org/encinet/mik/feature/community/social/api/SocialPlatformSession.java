package org.encinet.mik.feature.community.social.api;

import org.encinet.mik.feature.community.identity.IdentityMemberships;

import java.util.Optional;

/** Lifecycle handle for one platform transport generation. */
public interface SocialPlatformSession extends AutoCloseable {
    /** Required when the platform descriptor exposes identity binding. */
    default Optional<IdentityMemberships> memberships() {
        return Optional.empty();
    }

    /** Starts I/O only after the host has installed this session as current. */
    default void start() {
    }

    @Override
    void close();
}
