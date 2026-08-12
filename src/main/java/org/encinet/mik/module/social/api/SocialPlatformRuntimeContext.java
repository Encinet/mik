package org.encinet.mik.module.social.api;

import java.util.Objects;

/** Host callbacks captured by a platform transport opened for one generation. */
public record SocialPlatformRuntimeContext(
        SocialEventSink events,
        SocialSessionStatusSink status
) {
    public SocialPlatformRuntimeContext {
        events = Objects.requireNonNull(events, "events");
        status = Objects.requireNonNull(status, "status");
    }
}
