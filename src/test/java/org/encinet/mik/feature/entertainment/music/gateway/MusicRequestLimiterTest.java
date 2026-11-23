package org.encinet.mik.feature.entertainment.music.gateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicRequestLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final MusicRequestLimiter limiter = new MusicRequestLimiter(
            now::get,
            new MusicRequestLimiter.Policy(2, Duration.ofSeconds(5)),
            new MusicRequestLimiter.Policy(1, Duration.ofSeconds(10)));

    @Test
    void allowsBurstThenRefillsOnePermitAtATime() {
        UUID playerId = UUID.randomUUID();

        assertTrue(acquire(playerId, MusicRequestLimiter.Operation.SEARCH).allowed());
        assertTrue(acquire(playerId, MusicRequestLimiter.Operation.SEARCH).allowed());
        MusicRequestLimiter.Decision rejected =
                acquire(playerId, MusicRequestLimiter.Operation.SEARCH);

        assertFalse(rejected.allowed());
        assertEquals(5, rejected.retryAfterSeconds());

        now.addAndGet(Duration.ofMillis(4_100).toNanos());
        assertEquals(1, acquire(playerId, MusicRequestLimiter.Operation.SEARCH)
                .retryAfterSeconds());

        now.addAndGet(Duration.ofMillis(900).toNanos());
        assertTrue(acquire(playerId, MusicRequestLimiter.Operation.SEARCH).allowed());
        assertFalse(acquire(playerId, MusicRequestLimiter.Operation.SEARCH).allowed());
    }

    @Test
    void operationsAndPlayersUseIndependentBuckets() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(acquire(first, MusicRequestLimiter.Operation.PLAYBACK).allowed());
        assertFalse(acquire(first, MusicRequestLimiter.Operation.PLAYBACK).allowed());
        assertTrue(acquire(first, MusicRequestLimiter.Operation.SEARCH).allowed());
        assertTrue(acquire(second, MusicRequestLimiter.Operation.PLAYBACK).allowed());
    }

    @Test
    void forgettingPlayerRestoresFreshBuckets() {
        UUID playerId = UUID.randomUUID();
        assertTrue(acquire(playerId, MusicRequestLimiter.Operation.PLAYBACK).allowed());
        assertFalse(acquire(playerId, MusicRequestLimiter.Operation.PLAYBACK).allowed());

        limiter.forget(playerId);

        assertTrue(acquire(playerId, MusicRequestLimiter.Operation.PLAYBACK).allowed());
    }

    private MusicRequestLimiter.Decision acquire(
            UUID playerId, MusicRequestLimiter.Operation operation) {
        return limiter.tryAcquire(playerId, operation);
    }
}
