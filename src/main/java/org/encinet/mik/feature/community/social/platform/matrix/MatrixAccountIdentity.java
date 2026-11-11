package org.encinet.mik.feature.community.social.platform.matrix;

import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;

import java.util.Optional;

/** One Matrix account namespace, shared by messages, membership proofs and mention targets. */
final class MatrixAccountIdentity {
    private MatrixAccountIdentity() { }

    static Optional<ExternalIdentity> from(MatrixRoomMessage.MatrixMember member) {
        String userId = member.userId();
        int separator = serverSeparator(userId);
        if (separator < 0) return Optional.empty();
        try {
            return Optional.of(new ExternalIdentity(new ExternalIdentityKey(
                    MatrixPlatformConfig.PLATFORM_ID,
                    userId.substring(separator + 1), "", userId), member.displayName()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static boolean owns(ExternalIdentityKey key) {
        if (!key.platform().equals(MatrixPlatformConfig.PLATFORM_ID) || !key.scope().isEmpty()) return false;
        int separator = serverSeparator(key.subject());
        return separator >= 0 && key.issuer().equals(key.subject().substring(separator + 1));
    }

    private static int serverSeparator(String userId) {
        int separator = userId.indexOf(':', 1);
        return !userId.startsWith("@") || separator < 2 || separator == userId.length() - 1
                || userId.length() > 255 || userId.codePoints().anyMatch(Character::isISOControl)
                ? -1 : separator;
    }
}
