package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebChatAuthorizationTest {
    private static final long NOW = 1_800_000_000_000L;
    private JsonObject grant() {
        JsonObject grant = new JsonObject();
        grant.addProperty("playerUuid", "00000000-0000-4000-8000-000000000001");
        grant.addProperty("playerName", "Alice");
        grant.addProperty("connectionId", "A".repeat(32));
        grant.addProperty("clientPublicKey", WebChatEncryption.generate().publicKey());
        grant.addProperty("sessionRef", "_".repeat(43));
        grant.addProperty("authenticatedAt", NOW - 1000);
        grant.addProperty("checkedAt", NOW);
        grant.addProperty("expiresAt", NOW + 60_000);
        return grant;
    }

    @Test void acceptsTheAuthStoreBase64urlHashAndExactSocketLocator() {
        assertEquals("_".repeat(43), WebChatAuthorization.read(grant(), NOW).sessionRef());
    }

    @Test void rejectsExpiredOversizedAndNonNumericLeases() {
        JsonObject expired = grant(); expired.addProperty("expiresAt", NOW);
        assertThrows(IllegalArgumentException.class, () -> WebChatAuthorization.read(expired, NOW));
        JsonObject longLease = grant(); longLease.addProperty("expiresAt", NOW + 60_001);
        assertThrows(IllegalArgumentException.class, () -> WebChatAuthorization.read(longLease, NOW));
        JsonObject textDate = grant(); textDate.addProperty("checkedAt", Long.toString(NOW));
        assertThrows(IllegalArgumentException.class, () -> WebChatAuthorization.read(textDate, NOW));
        JsonObject fractional = grant(); fractional.addProperty("checkedAt", NOW + 0.5);
        assertThrows(ArithmeticException.class, () -> WebChatAuthorization.read(fractional, NOW));
    }

    @Test void rejectsLocatorOrIdentitySubstitution() {
        JsonObject id = grant(); id.addProperty("connectionId", "chosen");
        assertThrows(IllegalArgumentException.class, () -> WebChatAuthorization.read(id, NOW));
        JsonObject ref = grant(); ref.addProperty("sessionRef", "bearer-cookie-value");
        assertThrows(IllegalArgumentException.class, () -> WebChatAuthorization.read(ref, NOW));
        JsonObject name = grant(); name.addProperty("playerName", "\nadmin");
        assertThrows(IllegalArgumentException.class, () -> WebChatAuthorization.read(name, NOW));
    }
}
