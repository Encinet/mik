package org.encinet.mik.module.pvp;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

final class PvpOverrideRegistry {

    static final int MAX_ID_LENGTH = 80;

    private static final Comparator<Entry> PRECEDENCE = Comparator
            .comparingInt(Entry::priority)
            .thenComparingLong(Entry::sequence);

    private final LongSupplier clock;
    private final Map<UUID, Map<OverrideKey, Entry>> overrides = new HashMap<>();
    private long sequence;

    PvpOverrideRegistry() {
        this(System::currentTimeMillis);
    }

    PvpOverrideRegistry(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized void put(UUID playerId, String owner, String rawId,
                          boolean enabled, int priority, long durationMillis) {
        String normalizedOwner = normalizeOwner(owner);
        String id = normalizeId(rawId);
        if (durationMillis < PvpModule.PERMANENT_OVERRIDE) {
            throw new IllegalArgumentException("PVP override duration cannot be less than -1");
        }

        long now = clock.getAsLong();
        long expiresAt = durationMillis == PvpModule.PERMANENT_OVERRIDE
                ? 0L : saturatedAdd(now, durationMillis);
        overrides.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(new OverrideKey(normalizedOwner, id),
                        new Entry(normalizedOwner, id, enabled, priority, expiresAt, ++sequence));
    }

    synchronized boolean remove(UUID playerId, String owner, String rawId) {
        OverrideKey key = new OverrideKey(normalizeOwner(owner), normalizeId(rawId));
        Map<OverrideKey, Entry> playerOverrides = overrides.get(playerId);
        if (playerOverrides == null || playerOverrides.remove(key) == null) {
            return false;
        }
        if (playerOverrides.isEmpty()) {
            overrides.remove(playerId);
        }
        return true;
    }

    synchronized void clearAll(UUID playerId) {
        overrides.remove(playerId);
    }

    synchronized boolean clear(UUID playerId, String owner) {
        String normalizedOwner = normalizeOwner(owner);
        Map<OverrideKey, Entry> playerOverrides = overrides.get(playerId);
        if (playerOverrides == null) {
            return false;
        }
        boolean removed = playerOverrides.entrySet().removeIf(entry -> entry.getKey().owner().equals(normalizedOwner));
        if (playerOverrides.isEmpty()) {
            overrides.remove(playerId);
        }
        return removed;
    }

    synchronized Set<UUID> clearOwner(String owner) {
        String normalizedOwner = normalizeOwner(owner);
        Set<UUID> affected = new java.util.HashSet<>();
        var iterator = overrides.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Map<OverrideKey, Entry>> playerEntry = iterator.next();
            if (playerEntry.getValue().entrySet().removeIf(
                    entry -> entry.getKey().owner().equals(normalizedOwner))) {
                affected.add(playerEntry.getKey());
            }
            if (playerEntry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        return Set.copyOf(affected);
    }

    synchronized boolean hasOverride(UUID playerId) {
        return active(playerId).isPresent();
    }

    synchronized boolean contains(UUID playerId, String owner, String rawId) {
        OverrideKey key = new OverrideKey(normalizeOwner(owner), normalizeId(rawId));
        active(playerId);
        Map<OverrideKey, Entry> playerOverrides = overrides.get(playerId);
        return playerOverrides != null && playerOverrides.containsKey(key);
    }

    synchronized Set<String> ids(UUID playerId, String owner) {
        String normalizedOwner = normalizeOwner(owner);
        active(playerId);
        Map<OverrideKey, Entry> playerOverrides = overrides.get(playerId);
        if (playerOverrides == null) {
            return Set.of();
        }
        return playerOverrides.keySet().stream()
                .filter(key -> key.owner().equals(normalizedOwner))
                .map(OverrideKey::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    synchronized boolean effectiveEnabled(UUID playerId, boolean preference) {
        return active(playerId).map(Entry::enabled).orElse(preference);
    }

    synchronized Optional<PvpOverrideState> activeState(UUID playerId) {
        return active(playerId).map(Entry::state);
    }

    private Optional<Entry> active(UUID playerId) {
        Map<OverrideKey, Entry> playerOverrides = overrides.get(playerId);
        if (playerOverrides == null) {
            return Optional.empty();
        }

        long now = clock.getAsLong();
        playerOverrides.values().removeIf(entry -> entry.expiresAtMillis() > 0L
                && entry.expiresAtMillis() <= now);
        if (playerOverrides.isEmpty()) {
            overrides.remove(playerId);
            return Optional.empty();
        }
        return playerOverrides.values().stream().max(PRECEDENCE);
    }

    static String normalizeId(String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("PVP override id cannot be empty");
        }
        if (id.codePointCount(0, id.length()) > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("PVP override id cannot exceed " + MAX_ID_LENGTH + " characters");
        }
        return id;
    }

    private static String normalizeOwner(String owner) {
        String normalized = owner == null ? "" : owner.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("PVP override owner cannot be empty");
        }
        return normalized;
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private record Entry(
            String owner,
            String id,
            boolean enabled,
            int priority,
            long expiresAtMillis,
            long sequence
    ) {

        PvpOverrideState state() {
            return new PvpOverrideState(owner, id, enabled, priority, expiresAtMillis);
        }
    }

    private record OverrideKey(String owner, String id) {
    }
}
