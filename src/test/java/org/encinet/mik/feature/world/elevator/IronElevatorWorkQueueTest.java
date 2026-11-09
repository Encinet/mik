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
        input.priority(IronElevatorWorkQueue.INPUT);
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

    @Test
    void everyPriorityProgressesEvenWhenTheBudgetAllowsOnlyOneStepPerTick() {
        var clock = new AtomicLong();
        var queue = new IronElevatorWorkQueue(() -> clock.getAndAdd(1_000_000), error -> fail(error));
        var input = new AtomicInteger();
        var hint = new AtomicInteger();
        var catalog = new AtomicInteger();
        queue.submit(IronElevatorWorkQueue.INPUT, () -> { input.incrementAndGet(); return false; });
        queue.submit(IronElevatorWorkQueue.HINT, () -> { hint.incrementAndGet(); return false; });
        queue.submit(IronElevatorWorkQueue.CATALOG, () -> { catalog.incrementAndGet(); return false; });
        for (int tick = 0; tick < 70; tick++) queue.tick();
        assertEquals(40, input.get());
        assertEquals(20, hint.get());
        assertEquals(10, catalog.get());
    }

    @Test
    void runningTasksCanReturnToHintPriorityAndClearPreventsTheirRescheduling() {
        var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
        var steps = new AtomicInteger();
        IronElevatorWorkQueue.Task[] task = new IronElevatorWorkQueue.Task[1];
        task[0] = queue.submit(IronElevatorWorkQueue.INPUT, () -> {
            task[0].priority(IronElevatorWorkQueue.HINT);
            steps.incrementAndGet();
            queue.clear();
            return false;
        });
        queue.tick();
        assertEquals(1, steps.get());
        assertTrue(queue.empty());
    }
    @Test
    void backgroundSaturationLeavesAdmissionAndServiceForInput() {
        var clock = new AtomicLong();
        var queue = new IronElevatorWorkQueue(() -> clock.getAndAdd(1_000_000),error -> fail(error));
        int admitted = 0;
        for (int index = 0; index < 512; index++)
            if (queue.submit(IronElevatorWorkQueue.CATALOG,() -> false) != null) admitted++;
        assertTrue(admitted > 0 && admitted < 256);
        var input = new AtomicInteger();
        assertNotNull(queue.submit(IronElevatorWorkQueue.INPUT,() -> { input.incrementAndGet(); return true; }));
        queue.tick();
        assertEquals(1,input.get());
    }
}
