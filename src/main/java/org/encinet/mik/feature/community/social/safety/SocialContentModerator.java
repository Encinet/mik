package org.encinet.mik.feature.community.social.safety;

/** Blocking moderation capability, called only by the social workers. */
@FunctionalInterface
public interface SocialContentModerator extends AutoCloseable {
    boolean allows(String text);

    @Override
    default void close() {
    }
}
