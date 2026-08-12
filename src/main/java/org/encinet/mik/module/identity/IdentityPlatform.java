package org.encinet.mik.module.identity;

import java.util.Objects;

/** Metadata used by the Minecraft command UI for an installed platform adapter. */
public record IdentityPlatform(
        String id,
        String displayName,
        String redemptionInstruction,
        boolean scopeIsolated
) {

    public IdentityPlatform(String id, String displayName, String redemptionInstruction) {
        this(id, displayName, redemptionInstruction, false);
    }

    public IdentityPlatform {
        id = ExternalIdentityKey.normalizePlatform(id);
        displayName = requireText(displayName, "displayName", 40);
        redemptionInstruction = requireText(redemptionInstruction, "redemptionInstruction", 160);
        if (!redemptionInstruction.contains("{code}")) {
            throw new IllegalArgumentException(
                    "redemptionInstruction must contain the {code} placeholder");
        }
    }

    private static String requireText(String value, String name, int maximumLength) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException(name + " must not contain control characters");
            }
        });
        return normalized;
    }
}
