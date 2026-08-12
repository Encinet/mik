package org.encinet.mik.module.identity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityDomainModelTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void linkResultsRejectImpossibleStateCombinations() {
        IdentityBinding binding = binding();
        Instant retryAt = CREATED_AT.plusSeconds(60);

        assertEquals(binding, new IdentityLinkResult(
                IdentityLinkResult.Status.LINKED, binding, null).binding());
        assertEquals(retryAt, new IdentityLinkResult(
                IdentityLinkResult.Status.RATE_LIMITED, null, retryAt).retryAt());
        assertThrows(NullPointerException.class,
                () -> new IdentityLinkResult(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new IdentityLinkResult(
                IdentityLinkResult.Status.LINKED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new IdentityLinkResult(
                IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE, binding, null));
        assertThrows(IllegalArgumentException.class, () -> new IdentityLinkResult(
                IdentityLinkResult.Status.RATE_LIMITED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new IdentityLinkResult(
                IdentityLinkResult.Status.RATE_LIMITED, binding, retryAt));
    }

    @Test
    void bindingsEnforceTemporalAndPresentationInvariants() {
        ExternalIdentityKey key = key();

        IdentityBinding cleaned = new IdentityBinding(
                UUID.randomUUID(), "Alice", key, " External\nUser ",
                CREATED_AT, CREATED_AT.plusSeconds(1));

        assertEquals("External User", cleaned.externalDisplayName());
        assertThrows(IllegalArgumentException.class, () -> new IdentityBinding(
                UUID.randomUUID(), " ", key, "External",
                CREATED_AT, CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> new IdentityBinding(
                UUID.randomUUID(), "Alice", key, "External",
                CREATED_AT, CREATED_AT.minusSeconds(1)));
    }

    @Test
    void platformInstructionsMustBeSafeExecutableTemplates() {
        IdentityPlatform platform = new IdentityPlatform(
                "discord", "Discord", "/bind {code}", true);

        assertEquals("discord", platform.id());
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityPlatform("discord", "Discord", "/bind"));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityPlatform("discord", "Discord\nAdmin", "/bind {code}"));
    }

    private static IdentityBinding binding() {
        return new IdentityBinding(
                UUID.randomUUID(), "Alice", key(), "External",
                CREATED_AT, CREATED_AT);
    }

    private static ExternalIdentityKey key() {
        return new ExternalIdentityKey("qq", "app", "group", "member");
    }
}
