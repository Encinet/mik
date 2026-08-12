package org.encinet.mik.module.social.api;

/** Close handle for a running platform transport; status is pushed through its context. */
public interface SocialPlatformSession extends AutoCloseable {
    @Override
    void close();
}
