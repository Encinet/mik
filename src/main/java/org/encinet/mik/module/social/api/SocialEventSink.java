package org.encinet.mik.module.social.api;

/** Admission boundary called by platform transports after authentication. */
@FunctionalInterface
public interface SocialEventSink {
    Acceptance accept(SocialInboundMessage message);

    enum Acceptance {
        ACCEPTED,
        /** The transport may offer this non-command text to another shared feature. */
        UNHANDLED,
        IGNORED,
        RETRY_LATER
    }
}
