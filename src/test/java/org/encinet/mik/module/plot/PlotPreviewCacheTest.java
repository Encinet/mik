package org.encinet.mik.module.plot;

import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlotPreviewCacheTest {
    @Test
    void firstOpenAndRepeatedPendingRefreshesNeverTraverseOrHashTheCells() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicInteger traversals = new AtomicInteger();
        Set<Cell> guarded = new AbstractSet<>() {
            @Override public int size() { return 1; }
            @Override public int hashCode() { throw new AssertionError("Content-based cache key"); }
            @Override public boolean equals(Object other) { throw new AssertionError("Content-based cache comparison"); }
            @Override public Iterator<Cell> iterator() {
                assertNotSame(caller, Thread.currentThread());
                traversals.incrementAndGet();
                return Set.of(new Cell(0, 16, 0)).iterator();
            }
        };
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        try (var cache = new PlotPreviewCache(tasks::add)) {
            for (int count = 0; count < 1000; count++) assertNull(cache.request(guarded));
            assertEquals(0, traversals.get());
            assertEquals(1, tasks.size());
            Thread worker = new Thread(tasks.remove(), "preview-test-worker");
            worker.start();
            worker.join(5000);
            assertFalse(worker.isAlive());
            var ready = cache.request(guarded);
            assertNotNull(ready);
            int preparedTraversals = traversals.get();
            for (int count = 0; count < 1000; count++) assertSame(ready, cache.request(guarded));
            assertEquals(preparedTraversals, traversals.get());
            assertTrue(tasks.isEmpty());
        }
    }

    @Test
    void changedSnapshotsHaveIndependentResultsAndEvictedJobsCannotReturn() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger prepared = new AtomicInteger();
        try (var cache = new PlotPreviewCache(tasks::add, cells -> {
            prepared.incrementAndGet();
            return result(cells);
        })) {
            Set<Cell> first = Set.of(new Cell(0, 16, 0));
            assertNull(cache.request(first));
            Runnable evicted = tasks.remove();
            for (int index = 1; index <= PlotPreviewCache.CAPACITY; index++)
                assertNull(cache.request(Set.of(new Cell(index, 16, 0))));
            evicted.run();
            assertEquals(0, prepared.get());
            tasks.removeLast().run();
            Set<Cell> changed = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0));
            assertNull(cache.request(changed));
            tasks.removeLast().run();
            assertEquals(7, cache.request(changed).bounds().maximumX());
            assertNull(cache.request(first));
        }
    }

    @Test
    void shutdownCancelsQueuedWorkAndRefusesFurtherRequests() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger prepared = new AtomicInteger();
        var cache = new PlotPreviewCache(tasks::add, cells -> {
            prepared.incrementAndGet();
            return result(cells);
        });
        Set<Cell> cells = Set.of(new Cell(0, 16, 0));
        cache.request(cells);
        cache.close();
        tasks.remove().run();
        assertEquals(0, prepared.get());
        assertNull(cache.request(cells));
        assertThrows(RejectedExecutionException.class, () -> cache.execute(new FutureTask<>(() -> null)));
    }

    @Test
    void aRejectedJobCanBeRetriedWithoutLeavingAPermanentlyPendingEntry() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicBoolean reject = new AtomicBoolean(true);
        try (var cache = new PlotPreviewCache(task -> {
            if (reject.getAndSet(false)) throw new RejectedExecutionException();
            tasks.add(task);
        })) {
            Set<Cell> cells = Set.of(new Cell(0, 16, 0));
            assertNull(cache.request(cells));
            assertNull(cache.request(cells));
            assertEquals(1, tasks.size());
            tasks.remove().run();
            assertNotNull(cache.request(cells));
        }
    }

    @Test
    void fullHeightSolidBoxUsesTwelveAnalyticEdgesAndOneRegion() {
        Set<Cell> cells = new HashSet<>();
        for (int horizontal = 0; horizontal < 32; horizontal++)
            for (int vertical = -16; vertical < 80; vertical++)
                for (int forward = 0; forward < 32; forward++) cells.add(new Cell(horizontal, vertical, forward));
        assertEquals(98_304, cells.size());
        assertTimeout(Duration.ofSeconds(5), () -> {
            try (var cache = new PlotPreviewCache(Runnable::run)) {
                var ready = cache.request(Set.copyOf(cells));
                assertEquals(new PlotSelection.Bounds(0, -64, 0, 127, 319, 127), ready.bounds());
                assertEquals(1, ready.regions().size());
                var visible = ready.index().within(ready.bounds(), 100);
                assertEquals(12, visible.edges().size());
                assertFalse(visible.limited());
            }
        });
    }

    @Test
    void largeNegativeCoordinateCourtyardKeepsExactInnerAndOuterEdgesOffThread() throws Exception {
        Set<Cell> cells = new HashSet<>();
        for (int horizontal = -64; horizontal < 64; horizontal++)
            for (int vertical = -8; vertical < -2; vertical++)
                for (int forward = -64; forward < 64; forward++)
                    if (horizontal < -4 || horizontal >= 4 || forward < -4 || forward >= 4)
                        cells.add(new Cell(horizontal, vertical, forward));
        Set<Cell> snapshot = Set.copyOf(cells);
        CountDownLatch completed = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (var cache = new PlotPreviewCache(task -> executor.execute(() -> {
            try { task.run(); } finally { completed.countDown(); }
        }))) {
            assertNull(cache.request(snapshot));
            assertTrue(completed.await(15, TimeUnit.SECONDS));
            var ready = cache.request(snapshot);
            assertNotNull(ready);
            var edges = ready.index().within(ready.bounds(), 100).edges();
            assertEquals(24, edges.size());
            assertTrue(edges.contains(new PlotPreviewGeometry.Edge(new PlotPreviewGeometry.Point(-16, -32, -16),
                    new PlotPreviewGeometry.Point(-16, -8, -16))));
            var expected = PlotSelectionRegions.decompose(new PlotSelectionShape(java.util.UUID.randomUUID(), null, snapshot));
            assertEquals(expected, ready.regions());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closingTheWorkerInterruptsAnAcceptedRunningCalculation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var cache = new PlotPreviewCache(executor, cells -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException stop) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return result(cells);
        });
        try {
            var cells = Set.of(new Cell(0, 16, 0));
            assertNull(cache.request(cells));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cache.close();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            assertNull(cache.request(cells));
        } finally {
            cache.close();
            executor.shutdownNow();
        }
    }

    @Test
    void sharedWorkerUsesOneThreadAndABoundedQueueAndCancelsQueuedTasksOnShutdown() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        var queued = new ArrayList<FutureTask<Void>>();
        AtomicInteger rejected = new AtomicInteger();
        try (var cache = new PlotPreviewCache()) {
            FutureTask<Void> running = new FutureTask<>(() -> {
                assertEquals("mik-plot-preview", Thread.currentThread().getName());
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    stopped.countDown();
                }
                return null;
            });
            cache.execute(running);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int index = 0; index < 64; index++) {
                FutureTask<Void> pending = new FutureTask<>(() -> fail("Queued work unexpectedly ran"));
                try {
                    cache.execute(pending);
                    queued.add(pending);
                } catch (RejectedExecutionException full) {
                    rejected.incrementAndGet();
                }
            }
            assertEquals(PlotPreviewCache.CAPACITY, queued.size());
            assertEquals(64 - PlotPreviewCache.CAPACITY, rejected.get());
            cache.close();
            assertTrue(stopped.await(5, TimeUnit.SECONDS));
            assertTrue(queued.stream().allMatch(FutureTask::isCancelled));
        }
    }

    private static PlotPreviewCache.Prepared result(Set<Cell> cells) {
        var bounds = PlotPreviewGeometry.bounds(cells);
        return new PlotPreviewCache.Prepared(bounds, new PlotEdgeIndex(PlotPreviewGeometry.box(bounds)),
                java.util.List.of(new PlotSelectionRegions.Region(bounds)));
    }
}
