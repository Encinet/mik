package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.feature.community.identity.IdentityMemberships;
import org.encinet.mik.feature.community.identity.IdentityPlatform;

/** Host-owned bridge to the identity platform lease registry. */
@FunctionalInterface
public interface SocialIdentityLeaseManager {
    AutoCloseable register(IdentityPlatform platform,
            IdentityMemberships memberships);

    static SocialIdentityLeaseManager unavailable() {
        return (_, _) -> {
            throw new IllegalStateException("Identity binding service is unavailable");
        };
    }
}
