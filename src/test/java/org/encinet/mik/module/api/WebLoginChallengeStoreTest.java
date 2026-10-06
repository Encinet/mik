package org.encinet.mik.module.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebLoginChallengeStoreTest {
    @Test
    void confirmationExpiresAndReconfirmationReplacesConsumedState() {
        AtomicLong time = new AtomicLong(1_000L);
        WebLoginChallengeStore store = new WebLoginChallengeStore(time::get);
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        store.confirm("123456", firstPlayer, "First", "member");
        assertEquals(firstPlayer, store.consume("123456").playerUuid());
        assertTrue(store.find("123456").consumed());

        store.confirm("123456", secondPlayer, "Second", "custodian");
        assertEquals(secondPlayer, store.find("123456").playerUuid());
        assertFalse(store.find("123456").consumed());

        time.addAndGet(TimeUnit.MINUTES.toMillis(5));
        assertNull(store.find("123456"));
        assertNull(store.consume("123456"));
    }

    @Test
    void concurrentConsumptionConfirmsExactlyOneRequest() throws Exception {
        WebLoginChallengeStore store = new WebLoginChallengeStore();
        store.confirm("123456", UUID.randomUUID(), "Player", "member");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.consume("123456");
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return store.consume("123456");
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            WebLoginChallengeStore.Confirmation firstResult = first.get(5, TimeUnit.SECONDS);
            WebLoginChallengeStore.Confirmation secondResult = second.get(5, TimeUnit.SECONDS);
            assertNotNull(firstResult);
            assertNotNull(secondResult);
            assertEquals(1, (firstResult.consumed() ? 0 : 1)
                    + (secondResult.consumed() ? 0 : 1));
            assertTrue(store.find("123456").consumed());
        }
    }

    @Test
    void expiredReadCannotDeleteAReplacementConfirmation() throws Exception {
        AtomicLong time = new AtomicLong();
        AtomicBoolean pauseNextRead = new AtomicBoolean();
        CountDownLatch oldConfirmationRead = new CountDownLatch(1);
        CountDownLatch replacementStored = new CountDownLatch(1);
        WebLoginChallengeStore store = new WebLoginChallengeStore(() -> {
            if (pauseNextRead.compareAndSet(true, false)) {
                oldConfirmationRead.countDown();
                try {
                    if (!replacementStored.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("replacement was not stored");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            }
            return time.get();
        });
        store.confirm("123456", UUID.randomUUID(), "Old", "member");

        try (var executor = Executors.newSingleThreadExecutor()) {
            pauseNextRead.set(true);
            var staleRead = executor.submit(() -> store.find("123456"));
            assertTrue(oldConfirmationRead.await(5, TimeUnit.SECONDS));
            time.set(TimeUnit.MINUTES.toMillis(5));
            store.confirm("123456", UUID.randomUUID(), "New", "member");
            replacementStored.countDown();

            assertNull(staleRead.get(5, TimeUnit.SECONDS));
            assertEquals("New", store.find("123456").playerName());
        }
    }
}
