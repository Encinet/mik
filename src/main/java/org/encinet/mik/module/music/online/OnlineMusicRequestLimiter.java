package org.encinet.mik.module.music.online;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Per-player token buckets for player-triggered online music operations. */
public final class OnlineMusicRequestLimiter {

    private static final Policy SEARCH_POLICY = new Policy(2, Duration.ofSeconds(5));
    private static final Policy PLAYBACK_POLICY = new Policy(3, Duration.ofSeconds(10));

    private final ConcurrentHashMap<BucketKey, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;
    private final Policy searchPolicy;
    private final Policy playbackPolicy;

    public OnlineMusicRequestLimiter() {
        this(System::nanoTime, SEARCH_POLICY, PLAYBACK_POLICY);
    }

    OnlineMusicRequestLimiter(LongSupplier nanoTime, Policy searchPolicy,
                              Policy playbackPolicy) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.searchPolicy = Objects.requireNonNull(searchPolicy, "searchPolicy");
        this.playbackPolicy = Objects.requireNonNull(playbackPolicy, "playbackPolicy");
    }

    /** Consumes one permit or returns the rounded-up delay until the next permit. */
    public Decision tryAcquire(UUID playerId, Operation operation) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operation, "operation");
        Policy policy = policy(operation);
        long now = nanoTime.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(new BucketKey(playerId, operation),
                ignored -> new Bucket(policy.burst(), now));
        return bucket.tryAcquire(now, policy);
    }

    /** Removes all buckets when a player leaves so UUID state cannot accumulate indefinitely. */
    public void forget(UUID playerId) {
        if (playerId != null) {
            buckets.keySet().removeIf(key -> key.playerId().equals(playerId));
        }
    }

    private Policy policy(Operation operation) {
        return switch (operation) {
            case SEARCH -> searchPolicy;
            case PLAYBACK -> playbackPolicy;
        };
    }

    public enum Operation {
        SEARCH,
        PLAYBACK
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        private static Decision accepted() {
            return new Decision(true, 0);
        }

        private static Decision rejected(long retryAfterNanos) {
            long seconds = Math.max(1L, Math.ceilDiv(retryAfterNanos, 1_000_000_000L));
            return new Decision(false, seconds);
        }
    }

    record Policy(int burst, Duration refillInterval) {
        Policy {
            if (burst < 1) {
                throw new IllegalArgumentException("burst must be positive");
            }
            Objects.requireNonNull(refillInterval, "refillInterval");
            if (refillInterval.isZero() || refillInterval.isNegative()) {
                throw new IllegalArgumentException("refillInterval must be positive");
            }
        }

        long refillNanos() {
            return refillInterval.toNanos();
        }
    }

    private static final class Bucket {
        private int permits;
        private long lastRefillNanos;

        private Bucket(int permits, long now) {
            this.permits = permits;
            this.lastRefillNanos = now;
        }

        private synchronized Decision tryAcquire(long now, Policy policy) {
            long elapsed = Math.max(0L, now - lastRefillNanos);
            long refills = elapsed / policy.refillNanos();
            if (refills > 0) {
                permits = (int) Math.min(policy.burst(), permits + refills);
                lastRefillNanos += refills * policy.refillNanos();
            }
            if (permits > 0) {
                permits--;
                return Decision.accepted();
            }
            long untilNext = policy.refillNanos()
                    - Math.min(elapsed, policy.refillNanos() - 1L);
            return Decision.rejected(untilNext);
        }
    }

    private record BucketKey(UUID playerId, Operation operation) {
    }
}
