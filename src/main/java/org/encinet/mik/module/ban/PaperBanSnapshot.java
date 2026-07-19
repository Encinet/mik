package org.encinet.mik.module.ban;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

record PaperBanSnapshot(
        UUID playerUuid,
        String playerName,
        String reason,
        String source,
        Instant createdAt,
        Instant expiresAt
) {

    String normalizedName() {
        return playerName.toLowerCase(Locale.ROOT);
    }

    String identityKey() {
        return playerUuid != null ? "uuid:" + playerUuid : "name:" + normalizedName();
    }

    boolean sameContent(BanRecord record) {
        return (playerUuid == null || record.playerUuid() == null || playerUuid.equals(record.playerUuid()))
                && playerName.equals(record.playerName())
                && reason.equals(record.reason())
                && source.equals(record.source())
                && Objects.equals(expiresAt, record.expiresAt());
    }

    boolean samePaperEntry(PaperBanSnapshot other) {
        return other != null
                && Objects.equals(playerUuid, other.playerUuid)
                && playerName.equals(other.playerName)
                && reason.equals(other.reason)
                && source.equals(other.source)
                && createdAt.equals(other.createdAt)
                && Objects.equals(expiresAt, other.expiresAt);
    }

    boolean sameIdentity(PaperBanSnapshot other) {
        if (other == null) {
            return false;
        }
        if (playerUuid != null && other.playerUuid != null) {
            return playerUuid.equals(other.playerUuid);
        }
        return normalizedName().equals(other.normalizedName());
    }
}
