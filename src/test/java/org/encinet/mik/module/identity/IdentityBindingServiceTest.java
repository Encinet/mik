package org.encinet.mik.module.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityBindingServiceTest {

    @TempDir
    Path tempDirectory;

    private MutableClock clock;
    private IdentityBindingService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-10T00:00:00Z"));
        service = new IdentityBindingService(
                new IdentityBindingRepository(tempDirectory.resolve("identities.db").toFile()),
                clock,
                new SecureRandom());
        service.open();
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void codeCreatesDurableBindingAndCanOnlyBeConsumedOnce() throws Exception {
        UUID playerId = UUID.randomUUID();
        var issue = service.issueCode(playerId, "Alice", "QQ");
        ExternalIdentity identity = qqIdentity("app", "group-a", "member-a", "AliceQQ");

        assertTrue(issue.code().matches("[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}"));
        assertEquals(clock.instant().plus(IdentityBindingService.CODE_TTL), issue.expiresAt());
        String storedHash = storedCodeHash();
        assertEquals(64, storedHash.length());
        assertNotEquals(issue.code().replace("-", ""), storedHash);

        IdentityLinkResult linked = service.redeem(issue.code().toLowerCase(), identity);

        assertEquals(IdentityLinkResult.Status.LINKED, linked.status());
        assertEquals(playerId, linked.bindingOptional().orElseThrow().playerId());
        assertEquals(List.of(linked.binding()), service.bindings());
        assertEquals("AliceQQ", service.find(identity.key()).orElseThrow().externalDisplayName());
        assertEquals(IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE,
                service.redeem(issue.code(), identity).status());
    }

    @Test
    void newCodeReplacesOldCodeAndCanReverifyTheSameBinding() {
        UUID playerId = UUID.randomUUID();
        ExternalIdentity original = qqIdentity("app", "group", "member", "Old name");
        IdentityBinding binding = bind(playerId, "Alice", original);

        var replaced = service.issueCode(playerId, "Alice", "qq");
        var replacement = service.issueCode(playerId, "AliceRenamed", "qq");
        assertEquals(IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE,
                service.redeem(replaced.code(), original).status());

        IdentityLinkResult verified = service.redeem(replacement.code(),
                qqIdentity("app", "group", "member", "New name"));

        assertEquals(IdentityLinkResult.Status.ALREADY_LINKED, verified.status());
        assertEquals(binding.externalKey(),
                verified.bindingOptional().orElseThrow().externalKey());
        assertEquals("AliceRenamed", verified.binding().playerName());
        assertEquals("New name", verified.binding().externalDisplayName());
        assertEquals(1, service.findByPlayerName("alicerenamed").size());
    }

    @Test
    void samePlayerCanBindDifferentScopesButNotTwoSubjectsInOneScope() {
        UUID playerId = UUID.randomUUID();
        bind(playerId, "Alice", qqIdentity("app", "group-a", "member-a", "A"));

        var conflictingCode = service.issueCode(playerId, "Alice", "qq");
        assertEquals(IdentityLinkResult.Status.PLAYER_SCOPE_IN_USE,
                service.redeem(conflictingCode.code(),
                        qqIdentity("app", "group-a", "member-b", "B")).status());

        var otherGroupCode = service.issueCode(playerId, "Alice", "qq");
        assertEquals(IdentityLinkResult.Status.LINKED,
                service.redeem(otherGroupCode.code(),
                        qqIdentity("app", "group-b", "member-c", "C")).status());
        assertEquals(2, service.findByPlayer(playerId).size());
    }

    @Test
    void externalIdentityCannotBeClaimedByAnotherPlayer() {
        ExternalIdentity identity = qqIdentity("app", "group-a", "member-a", "A");
        bind(UUID.randomUUID(), "Alice", identity);

        var issue = service.issueCode(UUID.randomUUID(), "Bob", "qq");

        assertEquals(IdentityLinkResult.Status.EXTERNAL_IDENTITY_IN_USE,
                service.redeem(issue.code(), identity).status());
    }

    @Test
    void expiredAndWrongPlatformCodesAreRejected() {
        var expired = service.issueCode(UUID.randomUUID(), "Alice", "qq");
        clock.advance(IdentityBindingService.CODE_TTL.plusSeconds(1));
        assertEquals(IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE,
                service.redeem(expired.code(),
                        qqIdentity("app", "group", "member", "A")).status());

        var wrongPlatform = service.issueCode(UUID.randomUUID(), "Bob", "discord");
        assertEquals(IdentityLinkResult.Status.PLATFORM_MISMATCH,
                service.redeem(wrongPlatform.code(),
                        qqIdentity("app", "group", "other", "B")).status());
    }

    @Test
    void repeatedFailuresLockOnlyTheAttackingExternalIdentity() {
        ExternalIdentity attacker = qqIdentity("app", "group", "attacker", "A");
        var valid = service.issueCode(UUID.randomUUID(), "Alice", "qq");

        for (int attempt = 1; attempt < IdentityBindingService.MAX_FAILED_ATTEMPTS; attempt++) {
            assertEquals(IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE,
                    service.redeem("BAD", attacker).status());
        }
        IdentityLinkResult locked = service.redeem("BAD", attacker);
        assertEquals(IdentityLinkResult.Status.RATE_LIMITED, locked.status());
        assertNotNull(locked.retryAt());
        assertEquals(IdentityLinkResult.Status.RATE_LIMITED,
                service.redeem(valid.code(), attacker).status());

        clock.advance(IdentityBindingService.LOCKOUT_DURATION.plusSeconds(1));
        valid = service.issueCode(UUID.randomUUID(), "Alice", "qq");
        assertEquals(IdentityLinkResult.Status.LINKED,
                service.redeem(valid.code(), attacker).status());
    }

    @Test
    void cancelUnlinkAndReopenPreserveExpectedState() {
        UUID playerId = UUID.randomUUID();
        ExternalIdentity identity = qqIdentity("app", "group", "member", "A");
        var cancelled = service.issueCode(playerId, "Alice", "qq");
        assertTrue(service.cancelCode(playerId, "qq"));
        assertEquals(IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE,
                service.redeem(cancelled.code(), identity).status());

        IdentityBinding binding = bind(playerId, "Alice", identity);
        service.close();
        service = new IdentityBindingService(
                new IdentityBindingRepository(tempDirectory.resolve("identities.db").toFile()),
                clock,
                new SecureRandom());
        service.open();

        assertEquals(binding.externalKey(),
                service.find(identity.key()).orElseThrow().externalKey());
        assertEquals(1, service.unlinkPlayerPlatform(playerId, "qq").size());
        assertFalse(service.find(identity.key()).isPresent());
    }

    @Test
    void unlinkingAPlatformRemovesEveryScopedBindingForThatPlayer() {
        UUID playerId = UUID.randomUUID();
        ExternalIdentity first = qqIdentity("app", "group-a", "member-a", "A");
        ExternalIdentity second = qqIdentity("app", "group-b", "member-b", "B");
        bind(playerId, "Alice", first);
        bind(playerId, "Alice", second);

        var removed = service.unlinkPlayerPlatform(playerId, "QQ");

        assertEquals(2, removed.size());
        assertTrue(service.findByPlayer(playerId).isEmpty());
        assertTrue(service.find(first.key()).isEmpty());
        assertTrue(service.find(second.key()).isEmpty());
        assertTrue(service.bindings().isEmpty());
    }

    private IdentityBinding bind(UUID playerId, String playerName, ExternalIdentity identity) {
        var issue = service.issueCode(playerId, playerName, identity.key().platform());
        return service.redeem(issue.code(), identity).bindingOptional().orElseThrow();
    }

    private String storedCodeHash() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + tempDirectory.resolve("identities.db").toAbsolutePath());
             var statement = connection.createStatement();
             var results = statement.executeQuery(
                     "SELECT code_hash FROM identity_link_codes LIMIT 1")) {
            assertTrue(results.next());
            return results.getString(1);
        }
    }

    private static ExternalIdentity qqIdentity(
            String issuer,
            String scope,
            String subject,
            String displayName
    ) {
        return new ExternalIdentity(
                new ExternalIdentityKey("qq", issuer, scope, subject), displayName);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
