package org.encinet.mik.feature.community.social.api;

/** Receives transport lifecycle changes for exactly one platform generation. */
@FunctionalInterface
public interface SocialSessionStatusSink {
    void update(SocialSessionStatus status);
}
