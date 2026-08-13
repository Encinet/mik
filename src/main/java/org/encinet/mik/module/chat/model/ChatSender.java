package org.encinet.mik.module.chat.model;

import org.encinet.mik.module.identity.ExternalIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable sender identity resolved before rendering. */
public record ChatSender(
        String displayName,
        Optional<UUID> minecraftId,
        Optional<ExternalIdentity> externalIdentity,
        String minecraftName,
        String prefix,
        String suffix
) {
    public ChatSender {
        displayName = requireLine(displayName, "displayName", 128);
        minecraftId = minecraftId == null ? Optional.empty() : minecraftId;
        externalIdentity = externalIdentity == null
                ? Optional.empty() : externalIdentity;
        minecraftName = safeOptionalLine(minecraftName, "minecraftName", 64);
        prefix = Objects.requireNonNullElse(prefix, "");
        suffix = Objects.requireNonNullElse(suffix, "");
        if (prefix.length() > 1_024 || suffix.length() > 1_024
                || prefix.codePoints().anyMatch(Character::isISOControl)
                || suffix.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("sender decoration is invalid");
        }
        if (minecraftId.isPresent() && minecraftName.isBlank()) {
            throw new IllegalArgumentException(
                    "a Minecraft sender identity requires minecraftName");
        }
    }

    public static ChatSender minecraft(UUID playerId, String playerName) {
        return new ChatSender(playerName, Optional.of(playerId), Optional.empty(),
                playerName, "", "");
    }

    private static String safeOptionalLine(
            String value, String field, int maximumLength
    ) {
        String checked = Objects.requireNonNullElse(value, "").strip();
        if (checked.isEmpty()) {
            return "";
        }
        return requireLine(checked, field, maximumLength);
    }

    private static String requireLine(String value, String field, int maximumLength) {
        String checked = Objects.requireNonNull(value, field).strip();
        if (checked.isEmpty() || checked.length() > maximumLength
                || checked.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return checked;
    }
}
