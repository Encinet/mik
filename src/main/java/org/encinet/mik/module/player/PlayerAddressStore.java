package org.encinet.mik.module.player;

import org.encinet.mik.module.player.PlayerAssociationLookup.PlayerAssociation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerAddressStore {

    private static final Comparator<PlayerAssociation> ASSOCIATION_ORDER = (left, right) -> {
        int comparison = Integer.compare(right.sharedAddressCount(), left.sharedAddressCount());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(right.rarityScore(), left.rarityScore());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(right.coverage(), left.coverage());
        if (comparison != 0) {
            return comparison;
        }
        comparison = right.latestMatchedAt().compareTo(left.latestMatchedAt());
        if (comparison != 0) {
            return comparison;
        }
        return left.playerId().compareTo(right.playerId());
    };

    private final Map<String, AddressHistory> historiesByAddress = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, PlayerAddressRecord>> historiesByPlayer = new ConcurrentHashMap<>();

    void clear() {
        historiesByAddress.clear();
        historiesByPlayer.clear();
    }

    PlayerAddressRecord recordLogin(String address, UUID playerId, Instant now) {
        AddressHistory history = historiesByAddress.computeIfAbsent(address, ignored -> new AddressHistory());
        PlayerAddressRecord updated = history.record(address, playerId, now);
        indexByPlayer(updated);
        return updated;
    }

    void put(PlayerAddressRecord record) {
        historiesByAddress
                .computeIfAbsent(record.address(), ignored -> new AddressHistory())
                .put(record);
        indexByPlayer(record);
    }

    Optional<PlayerAddressRecord> latestByAddress(String address) {
        AddressHistory history = historiesByAddress.get(address);
        return history == null ? Optional.empty() : history.latest();
    }

    List<PlayerAddressRecord> recordsByAddress(String address) {
        AddressHistory history = historiesByAddress.get(address);
        return history == null ? List.of() : List.copyOf(history.records().values());
    }

    List<PlayerAssociation> findAssociations(UUID subjectId) {
        if (subjectId == null) {
            return List.of();
        }
        Map<String, PlayerAddressRecord> subjectHistory = historiesByPlayer.get(subjectId);
        if (subjectHistory == null || subjectHistory.isEmpty()) {
            return List.of();
        }

        Map<UUID, AssociationAccumulator> candidates = new HashMap<>();
        for (String address : subjectHistory.keySet()) {
            AddressHistory addressHistory = historiesByAddress.get(address);
            if (addressHistory == null) {
                continue;
            }
            int playerCount = addressHistory.records().size();
            if (playerCount < 2) {
                continue;
            }
            double rarity = 1.0D / playerCount;
            for (PlayerAddressRecord candidate : addressHistory.records().values()) {
                if (candidate.playerId().equals(subjectId)) {
                    continue;
                }
                candidates.computeIfAbsent(candidate.playerId(), ignored -> new AssociationAccumulator())
                        .add(candidate.lastSeenAt(), rarity);
            }
        }

        int subjectAddressCount = subjectHistory.size();
        return candidates.entrySet().stream()
                .map(entry -> association(entry.getKey(), entry.getValue(), subjectAddressCount))
                .sorted(ASSOCIATION_ORDER)
                .toList();
    }

    List<PlayerAddressRecord> snapshotRecords() {
        return historiesByAddress.values().stream()
                .flatMap(history -> history.records().values().stream())
                .sorted(Comparator.comparing(PlayerAddressRecord::address)
                        .thenComparing(PlayerAddressRecord::playerId))
                .toList();
    }

    private void indexByPlayer(PlayerAddressRecord record) {
        historiesByPlayer
                .computeIfAbsent(record.playerId(), ignored -> new ConcurrentHashMap<>())
                .put(record.address(), record);
    }

    private PlayerAssociation association(
            UUID candidateId,
            AssociationAccumulator accumulator,
            int subjectAddressCount
    ) {
        Map<String, PlayerAddressRecord> candidateHistory = historiesByPlayer.get(candidateId);
        int candidateAddressCount = candidateHistory == null ? 0 : candidateHistory.size();
        List<Instant> matchedTimes = accumulator.matchedLastSeenAt.stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        return new PlayerAssociation(
                candidateId,
                matchedTimes.size(),
                subjectAddressCount,
                candidateAddressCount,
                accumulator.rarityScore,
                matchedTimes);
    }

    record PlayerAddressRecord(String address, UUID playerId, int count, Instant lastSeenAt) {
    }

    private static final class AssociationAccumulator {
        private final List<Instant> matchedLastSeenAt = new ArrayList<>();
        private double rarityScore;

        void add(Instant lastSeenAt, double rarity) {
            matchedLastSeenAt.add(lastSeenAt);
            rarityScore += rarity;
        }
    }

    private static final class AddressHistory {
        private final Map<UUID, PlayerAddressRecord> records = new ConcurrentHashMap<>();
        private volatile PlayerAddressRecord latest;

        PlayerAddressRecord record(String address, UUID playerId, Instant now) {
            PlayerAddressRecord updated = records.compute(playerId, (ignored, current) -> new PlayerAddressRecord(
                    address,
                    playerId,
                    current == null ? 1 : current.count() + 1,
                    now));
            rememberLatest(updated);
            return updated;
        }

        void put(PlayerAddressRecord record) {
            records.put(record.playerId(), record);
            rememberLatest(record);
        }

        Optional<PlayerAddressRecord> latest() {
            return Optional.ofNullable(latest);
        }

        Map<UUID, PlayerAddressRecord> records() {
            return records;
        }

        private synchronized void rememberLatest(PlayerAddressRecord candidate) {
            PlayerAddressRecord current = latest;
            if (current == null || candidate.lastSeenAt().isAfter(current.lastSeenAt())) {
                latest = candidate;
            }
        }
    }
}
