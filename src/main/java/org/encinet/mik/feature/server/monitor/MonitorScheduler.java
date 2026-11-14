package org.encinet.mik.feature.server.monitor;

import org.encinet.mik.shared.role.PlayerRole;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread, shared weighted fair queue. Picks the least accumulated watching time divided by
 * role weight; ties favor the least recently selected player. Reservations prevent duplicate patrols.
 */
final class MonitorScheduler {
    private static final long RETENTION_NANOS = Duration.ofMinutes(30).toNanos();

    record Candidate(UUID id, PlayerRole role) { }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Assignment> assignments = new HashMap<>();
    private long accountedAt;
    private long order;
    private double virtualTime;

    void refresh(Collection<Candidate> eligible, long now) {
        advance(now);
        Set<UUID> assigned = new HashSet<>();
        assignments.values().forEach(assignment -> assigned.add(assignment.target));
        // Also expire history when patrols resume after a period with no monitoring sessions.
        entries.entrySet().removeIf(entry -> now - entry.getValue().lastActive > RETENTION_NANOS
                && !assigned.contains(entry.getKey()));
        Set<UUID> ids = new HashSet<>();
        eligible.forEach(candidate -> ids.add(candidate.id()));
        // Inactive players cannot bank service debt while offline, AFK or invisible.
        double floor = entries.entrySet().stream()
                .filter(entry -> ids.contains(entry.getKey()) && entry.getValue().eligible)
                .mapToDouble(entry -> entry.getValue().service).min().orElse(virtualTime);
        virtualTime = Math.max(virtualTime, floor);
        for (Candidate candidate : eligible) {
            Entry entry = entry(candidate.id());
            if (!entry.eligible) entry.service = Math.max(entry.service, virtualTime);
            entry.eligible = true;
            entry.lastActive = now;
        }
        entries.forEach((id, entry) -> entry.eligible = ids.contains(id));
    }

    @Nullable UUID select(UUID observer, Collection<Candidate> candidates, long now) {
        advance(now);
        Assignment previous = assignments.get(observer);
        Set<UUID> occupied = new HashSet<>();
        assignments.forEach((viewer, assignment) -> {
            if (!viewer.equals(observer)) occupied.add(assignment.target);
        });
        var available = candidates.stream().filter(candidate -> !occupied.contains(candidate.id())).toList();
        // Keep the camera when all other players are covered; otherwise alternate targets.
        var alternatives = available.stream()
                .filter(candidate -> previous == null || !candidate.id().equals(previous.target)).toList();
        if (!alternatives.isEmpty()) available = alternatives;
        Candidate selected = available.stream().min(Comparator
                .comparingDouble((Candidate candidate) -> entry(candidate.id()).service)
                .thenComparingLong(candidate -> entry(candidate.id()).order)).orElse(null);
        if (selected == null) {
            assignments.remove(observer);
            return null;
        }
        entry(selected.id()).order = ++order;
        assignments.put(observer, new Assignment(selected.id(), weight(selected.role()), false));
        return selected.id();
    }

    void watching(UUID observer, Candidate target, boolean watching, long now) {
        advance(now);
        entry(target.id()).lastActive = now;
        assignments.put(observer, new Assignment(target.id(), weight(target.role()), watching));
    }

    void release(UUID observer, long now) {
        advance(now);
        assignments.remove(observer);
    }

    boolean occupiedByOther(UUID observer, UUID target) {
        return assignments.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(observer) && entry.getValue().target.equals(target));
    }

    void clear() {
        entries.clear();
        assignments.clear();
        virtualTime = 0;
        order = 0;
        accountedAt = 0;
    }

    private Entry entry(UUID id) {
        return entries.computeIfAbsent(id, _ -> new Entry(virtualTime));
    }

    private void advance(long now) {
        long elapsed = Math.max(0, now - accountedAt);
        accountedAt = now;
        // Two manually pinned cameras still provide only one interval of coverage.
        Set<UUID> counted = new HashSet<>();
        for (Assignment assignment : assignments.values()) {
            if (assignment.watching && counted.add(assignment.target)) {
                entry(assignment.target).service += (double) elapsed / assignment.weight;
            }
        }
    }

    private static int weight(PlayerRole role) {
        return switch (role) {
            case DEFAULT -> 8;
            case MEMBER -> 4;
            case MODERATOR -> 2;
            case CUSTODIAN -> 1;
        };
    }

    private record Assignment(UUID target, int weight, boolean watching) { }

    private static final class Entry {
        double service;
        long order;
        long lastActive;
        boolean eligible;

        Entry(double service) { this.service = service; }
    }
}
