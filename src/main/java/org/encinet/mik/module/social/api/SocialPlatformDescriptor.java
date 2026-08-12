package org.encinet.mik.module.social.api;

import org.encinet.mik.module.identity.IdentityPlatform;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Stable platform metadata shared with the host and identity system. */
public record SocialPlatformDescriptor(
        String id,
        String displayName,
        Optional<IdentityPlatform> identityPlatform
) {
    public SocialPlatformDescriptor {
        id = requireId(id);
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        identityPlatform = identityPlatform == null ? Optional.empty() : identityPlatform;
        if (identityPlatform.isPresent()
                && !identityPlatform.get().id().equals(id)) {
            throw new IllegalArgumentException(
                    "identity platform id must match social platform id");
        }
    }

    public SocialPlatformDescriptor(String id, String displayName) {
        this(id, displayName, Optional.empty());
    }

    public SocialPlatformDescriptor(String id, String displayName,
                                    IdentityPlatform identityPlatform) {
        this(id, displayName, Optional.ofNullable(identityPlatform));
    }

    private static String requireId(String value) {
        String clean = Objects.requireNonNull(value, "id").strip().toLowerCase(Locale.ROOT);
        if (clean.isEmpty() || clean.chars().anyMatch(character ->
                !(character >= 'a' && character <= 'z'
                        || character >= '0' && character <= '9'
                        || character == '-' || character == '_'))) {
            throw new IllegalArgumentException("Invalid social platform id: " + value);
        }
        return clean;
    }
}
