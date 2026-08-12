package org.encinet.mik.module.social.api;

/** Receives transport lifecycle changes for exactly one platform generation. */
@FunctionalInterface
public interface SocialSessionStatusSink {
    void update(SocialSessionStatus status);
}
