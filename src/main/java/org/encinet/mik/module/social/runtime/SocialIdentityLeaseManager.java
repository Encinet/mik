package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.identity.IdentityPlatform;

/** Host-owned bridge to the identity platform lease registry. */
@FunctionalInterface
public interface SocialIdentityLeaseManager {
    AutoCloseable register(IdentityPlatform platform);

    static SocialIdentityLeaseManager unavailable() {
        return platform -> {
            throw new IllegalStateException("Identity binding service is unavailable");
        };
    }
}
