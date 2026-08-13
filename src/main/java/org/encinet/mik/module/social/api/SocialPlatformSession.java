package org.encinet.mik.module.social.api;

/** Lifecycle handle for one platform transport generation. */
public interface SocialPlatformSession extends AutoCloseable {
    /** Starts I/O only after the host has installed this session as current. */
    default void start() {
    }

    @Override
    void close();
}
