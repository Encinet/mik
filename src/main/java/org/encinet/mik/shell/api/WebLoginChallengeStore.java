package org.encinet.mik.shell.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Bounded, retryable game approvals. MikData owns one-time challenge consumption. */
final class WebLoginChallengeStore {
    private static final long VALIDITY_MILLIS = 5 * 60 * 1000L;
    private static final int MAX_CONFIRMATIONS = 1024;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9]{6,10}$");
    record Confirmation(UUID playerUuid, String playerName, String role, String confirmedAt, long expiresAtMillis) { }
    private record Pending(Confirmation confirmation, BooleanSupplier valid) { }
    private final Map<String, Pending> confirmations = new ConcurrentHashMap<>();
    private final LongSupplier currentTimeMillis;

    WebLoginChallengeStore() { this(System::currentTimeMillis); }
    WebLoginChallengeStore(LongSupplier currentTimeMillis) { this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis); }
    static boolean validCode(String code) { return code != null && CODE_PATTERN.matcher(code).matches(); }

    synchronized boolean confirm(String code, UUID playerUuid, String playerName, String role, BooleanSupplier valid) {
        Objects.requireNonNull(valid);
        if (!validCode(code) || !valid.getAsBoolean()) return false;
        long now = currentTimeMillis.getAsLong();
        confirmations.entrySet().removeIf(entry -> entry.getValue().confirmation().expiresAtMillis() <= now);
        Pending existing = confirmations.get(code);
        // Repeating a command never changes the approving account or extends its validity.
        if (existing != null) return existing.confirmation().playerUuid().equals(playerUuid) && existing.valid().getAsBoolean();
        if (confirmations.size() >= MAX_CONFIRMATIONS) return false;
        long owned = confirmations.values().stream().filter(value -> value.confirmation().playerUuid().equals(playerUuid)).count();
        if (owned >= 4) return false;
        confirmations.put(code, new Pending(new Confirmation(playerUuid, playerName, role,
                Instant.ofEpochMilli(now).toString(), now + VALIDITY_MILLIS), valid));
        return true;
    }

    Confirmation find(String code) {
        Pending pending = confirmations.get(code);
        if (pending == null) return null;
        if (pending.confirmation().expiresAtMillis() <= currentTimeMillis.getAsLong() || !pending.valid().getAsBoolean()) {
            confirmations.remove(code, pending);
            return null;
        }
        return pending.confirmation();
    }

    void clear() { confirmations.clear(); }
    void invalidate(UUID playerId) { confirmations.entrySet().removeIf(entry -> entry.getValue().confirmation().playerUuid().equals(playerId)); }
}
