package org.encinet.mik.module.music.online;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineMusicRequestLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final OnlineMusicRequestLimiter limiter = new OnlineMusicRequestLimiter(
            now::get,
            new OnlineMusicRequestLimiter.Policy(2, Duration.ofSeconds(5)),
            new OnlineMusicRequestLimiter.Policy(1, Duration.ofSeconds(10)));

    @Test
    void allowsBurstThenRefillsOnePermitAtATime() {
        UUID playerId = UUID.randomUUID();

        assertTrue(acquire(playerId, OnlineMusicRequestLimiter.Operation.SEARCH).allowed());
        assertTrue(acquire(playerId, OnlineMusicRequestLimiter.Operation.SEARCH).allowed());
        OnlineMusicRequestLimiter.Decision rejected =
                acquire(playerId, OnlineMusicRequestLimiter.Operation.SEARCH);

        assertFalse(rejected.allowed());
        assertEquals(5, rejected.retryAfterSeconds());

        now.addAndGet(Duration.ofMillis(4_100).toNanos());
        assertEquals(1, acquire(playerId, OnlineMusicRequestLimiter.Operation.SEARCH)
                .retryAfterSeconds());

        now.addAndGet(Duration.ofMillis(900).toNanos());
        assertTrue(acquire(playerId, OnlineMusicRequestLimiter.Operation.SEARCH).allowed());
        assertFalse(acquire(playerId, OnlineMusicRequestLimiter.Operation.SEARCH).allowed());
    }

    @Test
    void operationsAndPlayersUseIndependentBuckets() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(acquire(first, OnlineMusicRequestLimiter.Operation.PLAYBACK).allowed());
        assertFalse(acquire(first, OnlineMusicRequestLimiter.Operation.PLAYBACK).allowed());
        assertTrue(acquire(first, OnlineMusicRequestLimiter.Operation.SEARCH).allowed());
        assertTrue(acquire(second, OnlineMusicRequestLimiter.Operation.PLAYBACK).allowed());
    }

    @Test
    void forgettingPlayerRestoresFreshBuckets() {
        UUID playerId = UUID.randomUUID();
        assertTrue(acquire(playerId, OnlineMusicRequestLimiter.Operation.PLAYBACK).allowed());
        assertFalse(acquire(playerId, OnlineMusicRequestLimiter.Operation.PLAYBACK).allowed());

        limiter.forget(playerId);

        assertTrue(acquire(playerId, OnlineMusicRequestLimiter.Operation.PLAYBACK).allowed());
    }

    private OnlineMusicRequestLimiter.Decision acquire(
            UUID playerId, OnlineMusicRequestLimiter.Operation operation) {
        return limiter.tryAcquire(playerId, operation);
    }
}
