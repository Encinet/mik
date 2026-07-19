package org.encinet.mik.module.ban;

import org.encinet.mik.module.player.PlayerAddressLookup;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanAdmissionCheckerTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    private static final UUID JOINING_PLAYER = UUID.fromString("f12be267-fb05-4ca7-bda1-f3a38a7096de");
    private static final UUID BANNED_PLAYER = UUID.fromString("0706d64a-0957-4e31-b2f4-56e0ad5e9cea");

    @Test
    void directBanTakesPriorityOverAddressMatch() throws Exception {
        FakeBanManager bans = new FakeBanManager();
        BanRecord direct = ban(JOINING_PLAYER, "JoiningPlayer", NOW.plus(Duration.ofDays(2)));
        BanRecord linked = ban(BANNED_PLAYER, "BannedPlayer", null);
        bans.active.put(JOINING_PLAYER, direct);
        bans.active.put(BANNED_PLAYER, linked);
        FakeAddressLookup addresses = new FakeAddressLookup(
                List.of(new PlayerAddressLookup.AddressUse(BANNED_PLAYER, NOW)));

        BanAdmissionChecker.AdmissionBan result = checker(bans, addresses)
                .find(JOINING_PLAYER, "JoiningPlayer", InetAddress.getByName("203.0.113.10"))
                .orElseThrow();

        assertEquals(BanAdmissionChecker.MatchType.DIRECT, result.matchType());
        assertEquals(direct, result.record());
        assertTrue(addresses.cutoffs.isEmpty());
    }

    @Test
    void matchesActiveBanUsingAddressSeenWithinOneYear() throws Exception {
        FakeBanManager bans = new FakeBanManager();
        BanRecord linked = ban(BANNED_PLAYER, "BannedPlayer", null);
        bans.active.put(BANNED_PLAYER, linked);
        FakeAddressLookup addresses = new FakeAddressLookup(List.of(
                new PlayerAddressLookup.AddressUse(BANNED_PLAYER, NOW.minus(Duration.ofDays(364)))));

        BanAdmissionChecker.AdmissionBan result = checker(bans, addresses)
                .find(JOINING_PLAYER, "JoiningPlayer", InetAddress.getByName("203.0.113.10"))
                .orElseThrow();

        assertEquals(BanAdmissionChecker.MatchType.ADDRESS_LINK, result.matchType());
        assertEquals(linked, result.record());
        assertEquals(NOW.minus(Duration.ofDays(365)), addresses.cutoffs.getFirst());
    }

    @Test
    void includesExactlyOneYearButIgnoresOlderAddressUse() throws Exception {
        FakeBanManager bans = new FakeBanManager();
        bans.active.put(BANNED_PLAYER, ban(BANNED_PLAYER, "BannedPlayer", null));
        InetAddress address = InetAddress.getByName("203.0.113.10");

        FakeAddressLookup exactlyOneYear = new FakeAddressLookup(List.of(
                new PlayerAddressLookup.AddressUse(BANNED_PLAYER, NOW.minus(Duration.ofDays(365)))));
        assertTrue(checker(bans, exactlyOneYear).find(JOINING_PLAYER, "JoiningPlayer", address).isPresent());

        FakeAddressLookup older = new FakeAddressLookup(List.of(
                new PlayerAddressLookup.AddressUse(BANNED_PLAYER,
                        NOW.minus(Duration.ofDays(365)).minusMillis(1))));
        assertTrue(checker(bans, older).find(JOINING_PLAYER, "JoiningPlayer", address).isEmpty());
    }

    @Test
    void ignoresExpiredBanAndJoiningPlayersOwnAddressHistory() throws Exception {
        FakeBanManager bans = new FakeBanManager();
        FakeAddressLookup ownAddress = new FakeAddressLookup(List.of(
                new PlayerAddressLookup.AddressUse(JOINING_PLAYER, NOW)));
        assertTrue(checker(bans, ownAddress)
                .find(JOINING_PLAYER, "JoiningPlayer", InetAddress.getByName("203.0.113.10"))
                .isEmpty());

        FakeAddressLookup expiredBanAddress = new FakeAddressLookup(List.of(
                new PlayerAddressLookup.AddressUse(BANNED_PLAYER, NOW)));
        assertTrue(checker(bans, expiredBanAddress)
                .find(JOINING_PLAYER, "JoiningPlayer", InetAddress.getByName("203.0.113.10"))
                .isEmpty());
    }

    private BanAdmissionChecker checker(FakeBanManager bans, FakeAddressLookup addresses) {
        return new BanAdmissionChecker(bans, addresses, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private BanRecord ban(UUID playerId, String playerName, Instant expiration) {
        return new BanRecord(1, playerId, playerName, playerName.toLowerCase(), "reason", "Admin",
                NOW.minusSeconds(1), NOW.minusSeconds(1), expiration, null, BanRecord.Origin.MIK);
    }

    private static final class FakeAddressLookup implements PlayerAddressLookup {
        private final List<AddressUse> uses;
        private final List<Instant> cutoffs = new ArrayList<>();

        private FakeAddressLookup(List<AddressUse> uses) {
            this.uses = uses;
        }

        @Override
        public List<AddressUse> recentPlayersByAddress(InetAddress address, Instant notBefore) {
            cutoffs.add(notBefore);
            return uses.stream().filter(use -> !use.lastSeenAt().isBefore(notBefore)).toList();
        }
    }

    private static final class FakeBanManager implements BanManager {
        private final Map<UUID, BanRecord> active = new HashMap<>();

        @Override
        public Optional<BanRecord> active(UUID playerUuid, String playerName) {
            return Optional.ofNullable(active.get(playerUuid));
        }

        @Override
        public List<BanRecord> activeRecords() {
            return List.copyOf(active.values());
        }

        @Override
        public List<BanRecord> allRecords() {
            return activeRecords();
        }

        @Override
        public List<BanRecord> history(UUID playerUuid, String playerName) {
            return active(playerUuid, playerName).stream().toList();
        }

        @Override
        public BanRecord ban(UUID playerUuid, String playerName, String reason, String source,
                             Instant expiresAt, BanRecord.Origin origin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BanRecord editReason(UUID playerUuid, String playerName, String reason, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BanRecord editExpiration(UUID playerUuid, String playerName, Instant expiresAt, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BanRecord pardon(UUID playerUuid, String playerName, String operator, String reason) {
            throw new UnsupportedOperationException();
        }
    }
}
