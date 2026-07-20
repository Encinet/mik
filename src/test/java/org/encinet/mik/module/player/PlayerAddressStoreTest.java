package org.encinet.mik.module.player;

import org.encinet.mik.module.player.PlayerAssociationLookup.PlayerAssociation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAddressStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-21T00:00:00Z");
    private static final UUID SUBJECT = uuid(1);

    @Test
    void aggregatesDistinctSharedAddressesAndExcludesSubject() {
        PlayerAddressStore store = new PlayerAddressStore();
        UUID candidate = uuid(2);

        login(store, "203.0.113.10", SUBJECT, NOW.minus(Duration.ofDays(5)));
        login(store, "203.0.113.10", SUBJECT, NOW);
        login(store, "203.0.113.10", candidate, NOW.minus(Duration.ofDays(4)));
        login(store, "2001:db8::10", SUBJECT, NOW.minus(Duration.ofDays(3)));
        login(store, "2001:db8::10", candidate, NOW.minus(Duration.ofDays(2)));

        List<PlayerAssociation> result = store.findAssociations(SUBJECT);

        assertEquals(1, result.size());
        PlayerAssociation association = result.getFirst();
        assertEquals(candidate, association.playerId());
        assertEquals(2, association.sharedAddressCount());
        assertEquals(2, association.subjectAddressCount());
        assertEquals(2, association.candidateAddressCount());
        assertEquals(List.of(NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(4))),
                association.matchedLastSeenAt());
        assertFalse(result.stream().anyMatch(value -> value.playerId().equals(SUBJECT)));
        assertFalse(association.toString().contains("203.0.113.10"));
        assertFalse(association.toString().contains("2001:db8::10"));
        assertEquals(2, store.snapshotRecords().stream()
                .filter(record -> record.address().equals("203.0.113.10"))
                .filter(record -> record.playerId().equals(SUBJECT))
                .findFirst()
                .orElseThrow()
                .count());
    }

    @Test
    void sortsSharedAddressCountBeforeOtherSignals() {
        PlayerAddressStore store = new PlayerAddressStore();
        UUID oneMatch = uuid(2);
        UUID twoMatches = uuid(3);

        share(store, "address-a", oneMatch, NOW);
        share(store, "address-b", twoMatches, NOW.minus(Duration.ofDays(30)));
        login(store, "address-a", twoMatches, NOW.minus(Duration.ofDays(30)));

        List<PlayerAssociation> result = store.findAssociations(SUBJECT);

        assertEquals(twoMatches, result.getFirst().playerId());
        assertEquals(2, result.getFirst().sharedAddressCount());
    }

    @Test
    void ranksRareSharedAddressAheadOfPublicAddress() {
        PlayerAddressStore store = new PlayerAddressStore();
        UUID rareCandidate = uuid(2);
        UUID commonCandidate = uuid(3);

        share(store, "rare-address", rareCandidate, NOW.minus(Duration.ofDays(20)));
        share(store, "public-address", commonCandidate, NOW);
        login(store, "public-address", uuid(4), NOW);
        login(store, "public-address", uuid(5), NOW);

        List<PlayerAssociation> result = store.findAssociations(SUBJECT);

        assertEquals(rareCandidate, result.getFirst().playerId());
        assertTrue(result.getFirst().rarityScore() > result.get(1).rarityScore());
    }

    @Test
    void usesCoverageThenRecencyAsTieBreakers() {
        PlayerAddressStore coverageStore = new PlayerAddressStore();
        UUID completeHistory = uuid(2);
        UUID broadHistory = uuid(3);
        share(coverageStore, "shared-address", completeHistory, NOW.minus(Duration.ofDays(20)));
        login(coverageStore, "subject-only-address", SUBJECT, NOW);
        login(coverageStore, "shared-address", broadHistory, NOW);
        login(coverageStore, "unshared-address", broadHistory, NOW);

        List<PlayerAssociation> coverageResult = coverageStore.findAssociations(SUBJECT);
        assertEquals(completeHistory, coverageResult.getFirst().playerId());
        assertTrue(coverageResult.getFirst().coverage() > coverageResult.get(1).coverage());

        PlayerAddressStore recencyStore = new PlayerAddressStore();
        UUID older = uuid(4);
        UUID newer = uuid(5);
        share(recencyStore, "same-address", older, NOW.minus(Duration.ofDays(2)));
        login(recencyStore, "same-address", newer, NOW.minus(Duration.ofDays(1)));

        assertEquals(newer, recencyStore.findAssociations(SUBJECT).getFirst().playerId());
    }

    @Test
    void returnsEmptyForUnknownPlayer() {
        assertTrue(new PlayerAddressStore().findAssociations(SUBJECT).isEmpty());
    }

    @Test
    void rebuildsPlayerIndexFromPersistedRecords() {
        PlayerAddressStore original = new PlayerAddressStore();
        UUID candidate = uuid(2);
        share(original, "persisted-address", candidate, NOW.minus(Duration.ofDays(6)));

        PlayerAddressStore reloaded = new PlayerAddressStore();
        original.snapshotRecords().forEach(reloaded::put);

        PlayerAssociation association = reloaded.findAssociations(SUBJECT).getFirst();
        assertEquals(candidate, association.playerId());
        assertEquals(1, association.sharedAddressCount());
        assertEquals(List.of(NOW.minus(Duration.ofDays(6))), association.matchedLastSeenAt());
    }

    private static void share(PlayerAddressStore store, String address, UUID candidate, Instant candidateSeenAt) {
        login(store, address, SUBJECT, NOW);
        login(store, address, candidate, candidateSeenAt);
    }

    private static void login(PlayerAddressStore store, String address, UUID playerId, Instant at) {
        store.recordLogin(address, playerId, at);
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }
}
