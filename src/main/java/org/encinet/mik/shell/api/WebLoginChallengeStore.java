package org.encinet.mik.shell.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Thread-safe, expiring confirmations shared by game commands and local HTTP requests. */
final class WebLoginChallengeStore {
    private static final long VALIDITY_MILLIS = 5 * 60 * 1000L;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9]{6,10}$");

    record Confirmation(UUID playerUuid, String playerName, String role,
                        String confirmedAt, long expiresAtMillis, boolean consumed) {
        Confirmation consume() {
            return new Confirmation(playerUuid, playerName, role, confirmedAt,
                    expiresAtMillis, true);
        }
    }

    private record Pending(Confirmation confirmation, BooleanSupplier valid) { }

    private final Map<String, Pending> confirmations = new ConcurrentHashMap<>();
    private final LongSupplier currentTimeMillis;

    WebLoginChallengeStore() {
        this(System::currentTimeMillis);
    }

    WebLoginChallengeStore(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }

    static boolean validCode(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }

    void confirm(String code, UUID playerUuid, String playerName, String role, BooleanSupplier valid) {
        Objects.requireNonNull(valid, "valid");
        if (!validCode(code)) throw new IllegalArgumentException("Invalid web login code");
        long now = currentTimeMillis.getAsLong();
        cleanupExpired(now);
        confirmations.put(code, new Pending(new Confirmation(playerUuid, playerName, role,
                Instant.ofEpochMilli(now).toString(), now + VALIDITY_MILLIS, false), valid));
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

    Confirmation consume(String code) {
        AtomicReference<Confirmation> previous = new AtomicReference<>();
        confirmations.computeIfPresent(code, (key, pending) -> {
            Confirmation confirmation = pending.confirmation();
            if (confirmation.expiresAtMillis() <= currentTimeMillis.getAsLong() || !pending.valid().getAsBoolean()) return null;
            previous.set(confirmation);
            return confirmation.consumed() ? pending : new Pending(confirmation.consume(), pending.valid());
        });
        return previous.get();
    }

    void clear() {
        confirmations.clear();
    }

    void invalidate(UUID playerId) {
        confirmations.entrySet().removeIf(entry -> entry.getValue().confirmation().playerUuid().equals(playerId));
    }

    private void cleanupExpired(long now) {
        for (Map.Entry<String, Pending> entry : confirmations.entrySet()) {
            if (entry.getValue().confirmation().expiresAtMillis() <= now) {
                confirmations.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
