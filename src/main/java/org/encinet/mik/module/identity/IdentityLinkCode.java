package org.encinet.mik.module.identity;

import java.time.Instant;
import java.util.Objects;

/** A newly issued one-time code for an installed identity platform. */
public record IdentityLinkCode(
        String platform,
        String code,
        Instant expiresAt
) {

    public IdentityLinkCode {
        platform = ExternalIdentityKey.normalizePlatform(platform);
        code = requireCode(code);
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    private static String requireCode(String value) {
        String normalized = Objects.requireNonNull(value, "code").strip();
        if (normalized.isEmpty() || normalized.length() > 64
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("code has an invalid value");
        }
        return normalized;
    }
}
