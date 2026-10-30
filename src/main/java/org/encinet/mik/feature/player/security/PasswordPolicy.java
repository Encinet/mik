package org.encinet.mik.feature.player.security;
import org.encinet.mik.shared.role.PlayerRole;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

final class PasswordPolicy {
    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 128;
    private static final Set<String> COMMON = Set.of("password", "qwerty", "letmein", "minecraft", "1234567890");

    private PasswordPolicy() { }

    static boolean mandatory(PlayerRole role) {
        return role == PlayerRole.MODERATOR || role == PlayerRole.CUSTODIAN;
    }

    static boolean canReset(PlayerRole actor, PlayerRole target, boolean targetHasPassword, boolean self) {
        if (self || target == PlayerRole.CUSTODIAN) return false;
        if (actor == PlayerRole.CUSTODIAN) return true;
        return actor == PlayerRole.MODERATOR && (target != PlayerRole.MODERATOR || !targetHasPassword);
    }

    static String normalize(String raw) {
        if (raw == null || raw.length() > MAX_LENGTH * 2
                || raw.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.SURROGATE)) {
            throw new PasswordException(PasswordException.Code.WEAK);
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) throw new PasswordException(PasswordException.Code.WEAK);
        return normalized;
    }

    static String validate(String raw, String playerName) {
        String value = normalize(raw);
        String lower = value.toLowerCase(Locale.ROOT);
        String letters = lower.replaceAll("[^a-z0-9]", "");
        if (value.isBlank() || value.codePoints().distinct().count() < 5
                || (!playerName.isBlank() && lower.contains(playerName.toLowerCase(Locale.ROOT)))
                || COMMON.stream().anyMatch(word -> letters.contains(word))) {
            throw new PasswordException(PasswordException.Code.WEAK);
        }
        return value;
    }
}
