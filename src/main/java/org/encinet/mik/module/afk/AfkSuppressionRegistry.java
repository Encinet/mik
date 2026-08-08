package org.encinet.mik.module.afk;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks independently owned, idempotently released automatic-AFK leases. */
final class AfkSuppressionRegistry {

    private final Map<UUID, Map<UUID, String>> leasesByPlayer = new HashMap<>();

    synchronized AfkActivityService.ActivityLease acquire(UUID playerId, String reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("AFK suppression reason cannot be blank");
        }

        UUID leaseId = UUID.randomUUID();
        leasesByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(leaseId, reason);
        AtomicBoolean open = new AtomicBoolean(true);
        return () -> {
            if (open.compareAndSet(true, false)) {
                release(playerId, leaseId);
            }
        };
    }

    synchronized boolean isSuppressed(UUID playerId) {
        Map<UUID, String> leases = leasesByPlayer.get(playerId);
        return leases != null && !leases.isEmpty();
    }

    synchronized int leaseCount(UUID playerId) {
        Map<UUID, String> leases = leasesByPlayer.get(playerId);
        return leases == null ? 0 : leases.size();
    }

    synchronized void clear(UUID playerId) {
        leasesByPlayer.remove(playerId);
    }

    synchronized void clear() {
        leasesByPlayer.clear();
    }

    private synchronized void release(UUID playerId, UUID leaseId) {
        Map<UUID, String> leases = leasesByPlayer.get(playerId);
        if (leases == null) {
            return;
        }
        leases.remove(leaseId);
        if (leases.isEmpty()) {
            leasesByPlayer.remove(playerId);
        }
    }
}
