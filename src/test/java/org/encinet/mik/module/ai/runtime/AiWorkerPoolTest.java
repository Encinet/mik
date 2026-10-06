package org.encinet.mik.module.ai.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiWorkerPoolTest {
    @Test
    void usesVirtualThreadsWithSeparateConcurrencyAndQueueBounds() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean virtual = new AtomicBoolean();
        AtomicBoolean named = new AtomicBoolean();

        try (AiWorkerPool workers = new AiWorkerPool("test-ai-worker-", 1, 1)) {
            var first = workers.trySubmit(() -> {
                virtual.set(Thread.currentThread().isVirtual());
                named.set(Thread.currentThread().getName().startsWith("test-ai-worker-"));
                firstStarted.countDown();
                try {
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return 1;
            }).orElseThrow();
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            var second = workers.trySubmit(() -> 2).orElseThrow();
            assertTrue(awaitStatus(workers, 1, 1));
            assertTrue(workers.trySubmit(() -> 3).isEmpty());

            releaseFirst.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS));
            assertEquals(2, second.get(5, TimeUnit.SECONDS));
            assertTrue(virtual.get());
            assertTrue(named.get());
            assertEquals(1, workers.status().dropped());
        }
    }

    @Test
    void rejectsNewWorkAfterClose() {
        AiWorkerPool workers = new AiWorkerPool("closed-ai-worker-", 1, 0);
        workers.close();

        assertFalse(workers.tryExecute(() -> {
        }));
        assertTrue(workers.status().closed());
    }

    private static boolean awaitStatus(
            AiWorkerPool workers,
            int running,
            int queued
    ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            AiWorkerPool.Status status = workers.status();
            if (status.running() == running && status.queued() == queued) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }
}
