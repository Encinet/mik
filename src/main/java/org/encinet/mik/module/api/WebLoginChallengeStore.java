package org.encinet.mik.module.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
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

    private final Map<String, Confirmation> confirmations = new ConcurrentHashMap<>();
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

    void confirm(String code, UUID playerUuid, String playerName, String role) {
        if (!validCode(code)) throw new IllegalArgumentException("Invalid web login code");
        long now = currentTimeMillis.getAsLong();
        cleanupExpired(now);
        confirmations.put(code, new Confirmation(playerUuid, playerName, role,
                Instant.ofEpochMilli(now).toString(), now + VALIDITY_MILLIS, false));
    }

    Confirmation find(String code) {
        Confirmation confirmation = confirmations.get(code);
        if (confirmation != null && confirmation.expiresAtMillis() <= currentTimeMillis.getAsLong()) {
            confirmations.remove(code, confirmation);
            return null;
        }
        return confirmation;
    }

    Confirmation consume(String code) {
        AtomicReference<Confirmation> previous = new AtomicReference<>();
        confirmations.computeIfPresent(code, (key, confirmation) -> {
            if (confirmation.expiresAtMillis() <= currentTimeMillis.getAsLong()) {
                return null;
            }
            previous.set(confirmation);
            return confirmation.consumed() ? confirmation : confirmation.consume();
        });
        return previous.get();
    }

    void clear() {
        confirmations.clear();
    }

    private void cleanupExpired(long now) {
        for (Map.Entry<String, Confirmation> entry : confirmations.entrySet()) {
            if (entry.getValue().expiresAtMillis() <= now) {
                confirmations.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
