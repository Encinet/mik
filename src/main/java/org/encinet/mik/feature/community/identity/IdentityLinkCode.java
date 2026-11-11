package org.encinet.mik.feature.community.identity;

import java.time.Instant;
import java.util.Objects;

/** A newly issued one-time code for an installed identity platform. */
public record IdentityLinkCode(
        String platform,
        String code,
        Instant expiresAt
) {

    static final int CODE_LENGTH = 6;

    public IdentityLinkCode {
        platform = ExternalIdentityKey.normalizePlatform(platform);
        code = requireCode(code);
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    static String normalizeCode(String rawCode) {
        if (rawCode == null) return null;
        String code = rawCode.strip();
        if (code.length() != CODE_LENGTH) return null;
        for (int index = 0; index < code.length(); index++) {
            char digit = code.charAt(index);
            if (digit < '0' || digit > '9') return null;
        }
        return code;
    }

    private static String requireCode(String value) {
        String normalized = normalizeCode(Objects.requireNonNull(value, "code"));
        if (normalized == null) throw new IllegalArgumentException("code must contain exactly six ASCII digits");
        return normalized;
    }
}
