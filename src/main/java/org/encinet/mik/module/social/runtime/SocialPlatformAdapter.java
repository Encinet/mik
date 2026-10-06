package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.social.api.SocialPlatformDescriptor;

import java.util.Optional;

/** Two-phase platform SPI: validate configuration first, then open a transport session. */
public interface SocialPlatformAdapter {
    SocialPlatformDescriptor descriptor();

    /** Returns empty when this platform is deliberately disabled by configuration. */
    Optional<SocialPlatformPlan> prepare();
}
