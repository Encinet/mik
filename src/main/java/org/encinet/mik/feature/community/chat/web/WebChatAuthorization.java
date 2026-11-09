package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Wire contract for the trusted AuthStore callback. Session references are SHA-256 base64url, not bearer tokens. */
record WebChatAuthorization(UUID playerUuid, String playerName, String sessionRef, String connectionId,
        String clientPublicKey, long authenticatedAt, long checkedAt, long expiresAt) {
    static WebChatAuthorization read(JsonObject body, long now) {
        String rawUuid = text(body, "playerUuid");
        UUID player = UUID.fromString(rawUuid);
        String ref = text(body, "sessionRef"), id = text(body, "connectionId"), name = text(body, "playerName");
        String clientKey = text(body, "clientPublicKey");
        long authenticated = number(body, "authenticatedAt"), checked = number(body, "checkedAt"), expires = number(body, "expiresAt");
        if (!player.toString().equals(rawUuid) || !ref.matches("[A-Za-z0-9_-]{43}") || !id.matches("[A-Za-z0-9_-]{32}")
                || name.isBlank() || name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl) || checked > now + 5000 || checked < now - 60_000
                || expires <= now || expires > checked + 60_000 || authenticated <= 0 || authenticated > checked + 5000)
            throw new IllegalArgumentException("invalid_grant");
        WebChatEncryption.suite(org.bouncycastle.crypto.hpke.HPKE.mode_base)
                .deserializePublicKey(WebChatEncryption.decode(clientKey, 65));
        return new WebChatAuthorization(player, name, ref, id, clientKey, authenticated, checked, expires);
    }
    private static String text(JsonObject body, String key) {
        var value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("invalid_grant");
        return value.getAsString();
    }
    private static long number(JsonObject body, String key) {
        var value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("invalid_grant");
        return value.getAsBigDecimal().longValueExact();
    }
}
