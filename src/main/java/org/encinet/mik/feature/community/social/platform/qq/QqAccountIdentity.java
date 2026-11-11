package org.encinet.mik.feature.community.social.platform.qq;

import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;

import java.util.Optional;

/** QQ account identifiers belong to an AppID; conversation membership is separate. */
final class QqAccountIdentity {
    private QqAccountIdentity() { }

    static Optional<ExternalIdentity> from(String appId, String memberOpenId, String displayName) {
        if (memberOpenId.isBlank()) return Optional.empty();
        try {
            return Optional.of(new ExternalIdentity(new ExternalIdentityKey(
                    QqPlatformConfig.PLATFORM_ID, appId, "", memberOpenId), displayName));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static boolean belongsTo(String appId, ExternalIdentityKey key) {
        return key.platform().equals(QqPlatformConfig.PLATFORM_ID)
                && key.issuer().equals(appId) && key.scope().isEmpty();
    }
}
