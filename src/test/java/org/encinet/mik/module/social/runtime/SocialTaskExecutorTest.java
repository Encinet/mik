package org.encinet.mik.module.social.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialTaskExecutorTest {

    @Test
    void preservesOrderWithinAConversation() throws Exception {
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        try (SocialTaskExecutor executor = new SocialTaskExecutor(4, "ordered-test-")) {
            assertTrue(executor.submitOrdered("room", () -> {
                firstStarted.countDown();
                await(releaseFirst);
                order.add(1);
                finished.countDown();
            }));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            assertTrue(executor.submitOrdered("room", () -> {
                order.add(2);
                finished.countDown();
            }));

            Thread.sleep(25);
            assertEquals(List.of(), order);
            releaseFirst.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of(1, 2), order);
        }
    }

    @Test
    void allowsDifferentConversationsToProgressConcurrently() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (SocialTaskExecutor executor = new SocialTaskExecutor(2, "parallel-test-")) {
            assertTrue(executor.submitOrdered("first", () -> {
                firstStarted.countDown();
                await(release);
            }));
            assertTrue(executor.submitOrdered("second", () -> {
                secondStarted.countDown();
                await(release);
            }));

            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            release.countDown();
        }
    }

    @Test
    void queuesABurstWithinTheSameConversation() throws Exception {
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(32);
        List<Integer> order = new CopyOnWriteArrayList<>();
        try (SocialTaskExecutor executor = new SocialTaskExecutor(1, "burst-test-")) {
            for (int index = 0; index < 32; index++) {
                int sequence = index;
                assertTrue(executor.submitOrdered("room", () -> {
                    if (sequence == 0) {
                        await(releaseFirst);
                    }
                    order.add(sequence);
                    finished.countDown();
                }));
            }
            releaseFirst.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(java.util.stream.IntStream.range(0, 32)
                    .boxed().toList(), order);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
