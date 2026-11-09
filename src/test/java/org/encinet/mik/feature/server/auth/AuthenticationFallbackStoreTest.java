package org.encinet.mik.feature.server.auth;

import com.mojang.authlib.services.ProfileResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;

import static org.encinet.mik.feature.server.auth.AuthenticationTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationFallbackStoreTest {
    @TempDir Path directory;
    private final MutableClock clock = new MutableClock();
    private final Duration maxAge = Duration.ofHours(72);
    private final ProfileResult alice = profile(UUID.randomUUID(), "Alice");

    private AuthenticationFallbackStore open(int capacity) throws Exception {
        var store = new AuthenticationFallbackStore(directory.resolve("cache.db"), clock, maxAge, capacity);
        store.open();
        return store;
    }

    @Test
    void restartPreservesUuidSignedTexturesAndProfileActions() throws Exception {
        try (var store = open(10)) { store.remember("aLiCe", ADDRESS, alice); }
        try (var store = open(10)) {
            var cached = store.find("ALICE", ADDRESS);
            assertEquals(alice.profile().id(), cached.profile().id());
            assertEquals("Alice", cached.profile().name());
            assertEquals(alice.profile().properties(), cached.profile().properties());
            assertEquals(alice.actions(), cached.actions());
            assertNull(store.find("Alice", OTHER_ADDRESS));
            assertNull(store.find("Unknown", ADDRESS));
            assertNull(store.find("Alice", null));
        }
    }

    @Test
    void exactExpiryAndBackwardsClockDenyCachedAuthentication() throws Exception {
        try (var store = open(10)) {
            store.remember("Alice", ADDRESS, alice);
            clock.advance(-1);
            assertNull(store.find("Alice", ADDRESS));
            clock.advance(maxAge.toMillis());
            assertNotNull(store.find("Alice", ADDRESS));
            clock.advance(1);
            assertNull(store.find("Alice", ADDRESS));
        }
    }

    @Test
    void onlyTheLatestVerifiedAddressSurvives() throws Exception {
        try (var store = open(10)) {
            store.remember("Alice", ADDRESS, alice);
            store.remember("Alice", OTHER_ADDRESS, alice);
            assertNull(store.find("Alice", ADDRESS));
            assertNotNull(store.find("Alice", OTHER_ADDRESS));
        }
    }

    @Test
    void renamingOrReassigningANameRemovesStaleIdentityBindings() throws Exception {
        try (var store = open(10)) {
            store.remember("Alice", ADDRESS, alice);
            store.remember("Bob", OTHER_ADDRESS, profile(alice.profile().id(), "Bob"));
            assertNull(store.find("Alice", ADDRESS));
            assertEquals(alice.profile().id(), store.find("Bob", OTHER_ADDRESS).profile().id());
            var newBob = profile(UUID.randomUUID(), "Bob");
            store.remember("Bob", ADDRESS, newBob);
            assertNull(store.find("Bob", OTHER_ADDRESS));
            assertEquals(newBob.profile().id(), store.find("Bob", ADDRESS).profile().id());
        }
    }

    @Test
    void missingAddressAndMismatchedUpstreamNameCannotSeedCache() throws Exception {
        try (var store = open(10)) {
            store.remember("Alice", null, alice);
            store.remember("Mallory", ADDRESS, alice);
            assertNull(store.find("Alice", ADDRESS));
            assertNull(store.find("Mallory", ADDRESS));
        }
    }

    @Test
    void ipv6BindingsCompareAddressBytes() throws Exception {
        var ipv6 = address(new byte[]{0x20, 1, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        try (var store = open(10)) {
            store.remember("Alice", ipv6, alice);
            assertNotNull(store.find("Alice", address(ipv6.getAddress())));
            assertNull(store.find("Alice", ADDRESS));
        }
    }

    @Test
    void capacityEvictsTheOldestVerificationAndExpirySurvivesRestart() throws Exception {
        try (var store = open(1)) {
            store.remember("Alice", ADDRESS, alice);
            clock.advance(1);
            store.remember("Bob", ADDRESS, profile(UUID.randomUUID(), "Bob"));
            assertNull(store.find("Alice", ADDRESS));
            assertNotNull(store.find("Bob", ADDRESS));
        }
        clock.advance(maxAge.toMillis());
        try (var store = open(1)) { assertNull(store.find("Bob", ADDRESS)); }
    }

    @Test
    void malformedExistingDatabaseIsPreserved() throws Exception {
        Path path = directory.resolve("cache.db");
        byte[] original = "damaged database".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(path, original);
        assertThrows(Exception.class, () -> open(10));
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test
    void validSqliteWithMismatchedProfileIdentityIsRejected() throws Exception {
        try (var store = open(10)) { store.remember("Alice", ADDRESS, alice); }
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("cache.db"));
             var statement = db.createStatement()) {
            statement.executeUpdate("UPDATE sessions SET name='Mallory'");
        }
        assertThrows(Exception.class, () -> open(10));
    }
}
