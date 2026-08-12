package org.encinet.mik.module.identity;

import java.util.Locale;
import java.util.Objects;

/**
 * A platform-issued principal. The issuer and scope are deliberately part of the key because
 * identifier stability differs between platforms and even between conversations.
 */
public record ExternalIdentityKey(
        String platform,
        String issuer,
        String scope,
        String subject
) {

    private static final int MAX_PLATFORM_LENGTH = 32;
    private static final int MAX_KEY_PART_LENGTH = 256;

    public ExternalIdentityKey {
        platform = normalizePlatform(platform);
        issuer = requireKeyPart(issuer, "issuer", false);
        scope = requireKeyPart(scope, "scope", true);
        subject = requireKeyPart(subject, "subject", false);
    }

    public static String normalizePlatform(String value) {
        String normalized = Objects.requireNonNull(value, "platform")
                .strip()
                .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_PLATFORM_LENGTH) {
            throw new IllegalArgumentException("platform must contain 1-32 characters");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            boolean accepted = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '.' || character == '_' || character == '-';
            if (!accepted) {
                throw new IllegalArgumentException(
                        "platform may only contain lowercase ASCII letters, digits, '.', '_' and '-'");
            }
        }
        return normalized;
    }

    private static String requireKeyPart(String value, String name, boolean mayBeEmpty) {
        String opaqueValue = Objects.requireNonNull(value, name);
        if ((opaqueValue.isEmpty() && !mayBeEmpty)
                || (!opaqueValue.isEmpty() && opaqueValue.isBlank())
                || opaqueValue.length() > MAX_KEY_PART_LENGTH) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        opaqueValue.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException(name + " must not contain control characters");
            }
        });
        return opaqueValue;
    }
}
