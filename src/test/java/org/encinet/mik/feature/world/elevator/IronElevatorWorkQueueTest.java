package org.encinet.mik.feature.world.elevator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class IronElevatorWorkQueueTest {
    @Test
    void appliesOneGlobalStepLimitAndSharesStepsBetweenEqualPriorities() {
        var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        queue.submit(IronElevatorWorkQueue.HINT, () -> { first.incrementAndGet(); return false; });
        queue.submit(IronElevatorWorkQueue.HINT, () -> { second.incrementAndGet(); return false; });
        queue.tick();
        assertEquals(128, first.get());
        assertEquals(128, second.get());
    }

    @Test
    void prioritizesInputAndStopsAtTheTimeBudget() {
        var clock = new AtomicLong();
        var queue = new IronElevatorWorkQueue(() -> clock.getAndAdd(600_000), error -> fail(error));
        var order = new ArrayList<String>();
        queue.submit(IronElevatorWorkQueue.CATALOG, () -> { order.add("catalog"); return true; });
        var input = queue.submit(IronElevatorWorkQueue.HINT, () -> { order.add("input"); return true; });
        queue.submit(IronElevatorWorkQueue.HINT, () -> { order.add("hint"); return true; });
        input.prioritizeInput();
        queue.tick();
        assertEquals(java.util.List.of("input", "hint"), order);
        assertFalse(queue.empty());
        queue.tick();
        assertEquals(java.util.List.of("input", "hint", "catalog"), order);
    }

    @Test
    void cancellationAndFailureDoNotStopOtherQueries() {
        var failures = new ArrayList<RuntimeException>();
        var queue = new IronElevatorWorkQueue(() -> 0, failures::add);
        var cancelled = queue.submit(IronElevatorWorkQueue.INPUT, () -> { fail("cancelled task ran"); return true; });
        cancelled.cancel();
        queue.submit(IronElevatorWorkQueue.INPUT, () -> { throw new IllegalStateException("failure"); });
        var ran = new AtomicInteger();
        queue.submit(IronElevatorWorkQueue.HINT, () -> { ran.incrementAndGet(); return true; });
        queue.tick();
        assertEquals(1, failures.size());
        assertEquals(1, ran.get());
        assertTrue(queue.empty());
    }
}
