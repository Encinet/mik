package org.encinet.mik.module.social.runtime;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bounded, expiring event-key set used independently by each platform generation. */
public final class SocialEventDeduplicator {
    private final Map<String, Long> seen = new LinkedHashMap<>();
    private final int maximumSize;
    private final long ttlMillis;
    private final LongSupplier clock;

    public SocialEventDeduplicator(int maximumSize, Duration ttl) {
        this(maximumSize, ttl.toMillis(), System::currentTimeMillis);
    }

    SocialEventDeduplicator(int maximumSize, long ttlMillis, LongSupplier clock) {
        if (maximumSize < 1 || ttlMillis < 1) {
            throw new IllegalArgumentException("deduplication bounds must be positive");
        }
        this.maximumSize = maximumSize;
        this.ttlMillis = ttlMillis;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized boolean accept(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        long now = clock.getAsLong();
        removeExpired(now);
        if (seen.putIfAbsent(key, now) != null) {
            return false;
        }
        while (seen.size() > maximumSize) {
            Iterator<String> iterator = seen.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    public synchronized void forget(String key) {
        seen.remove(key);
    }

    public synchronized void clear() {
        seen.clear();
    }

    private void removeExpired(long now) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() < ttlMillis) {
                break;
            }
            iterator.remove();
        }
    }
}
