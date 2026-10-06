package org.encinet.mik.module.plot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class PlotPreviewCache implements AutoCloseable {
    static final int CAPACITY = 16;
    private static final UUID GEOMETRY_WORLD = new UUID(0, 0);
    private final Map<Identity, Entry> entries = new LinkedHashMap<>(CAPACITY, 0.75F, true);
    private final Executor executor;
    private final Function<Set<PlotGeometry.Cell>, Prepared> prepare;
    private boolean closed;

    record Prepared(PlotSelection.Bounds bounds, PlotEdgeIndex index, List<PlotSelectionRegions.Region> regions) { }

    PlotPreviewCache() {
        this(new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(CAPACITY), runnable -> {
            Thread worker = new Thread(runnable, "mik-plot-preview");
            worker.setDaemon(true);
            return worker;
        }));
    }

    PlotPreviewCache(Executor executor) { this(executor, PlotPreviewCache::prepare); }

    PlotPreviewCache(Executor executor, Function<Set<PlotGeometry.Cell>, Prepared> prepare) {
        this.executor = executor;
        this.prepare = prepare;
    }

    synchronized Prepared request(Set<PlotGeometry.Cell> cells) {
        if (closed || cells.isEmpty()) return null;
        Identity key = new Identity(cells);
        Entry entry = entries.get(key);
        if (entry != null) return entry.result;
        if (entries.size() == CAPACITY) {
            var oldest = entries.entrySet().iterator();
            Entry removed = oldest.next().getValue();
            oldest.remove();
            cancel(removed);
        }
        Entry created = new Entry();
        created.task = new FutureTask<>(() -> {
            try {
                Prepared result = prepare.apply(cells);
                if (!Thread.currentThread().isInterrupted() && !created.task.isCancelled()) created.result = result;
            } catch (CancellationException ignored) {
            } catch (RuntimeException failure) {
                System.getLogger(PlotPreviewCache.class.getName()).log(System.Logger.Level.ERROR,
                        "Could not prepare plot preview", failure);
            }
            return null;
        });
        entries.put(key, created);
        try {
            executor.execute(created.task);
        } catch (RejectedExecutionException rejected) {
            entries.remove(key);
            cancel(created);
        }
        return created.result;
    }

    PlotSelection.Bounds bounds(PlotSelectionShape shape) {
        if (shape == null || shape.empty()) return null;
        if (!shape.composite()) return shape.bounds();
        Prepared prepared = request(shape.cells());
        return prepared == null ? null : prepared.bounds();
    }

    List<PlotSelectionRegions.Region> regions(PlotSelectionShape shape) {
        if (shape == null || shape.empty()) return List.of();
        if (!shape.composite()) return List.of(new PlotSelectionRegions.Region(shape.alignedBounds()));
        Prepared prepared = request(shape.cells());
        return prepared == null ? List.of() : prepared.regions();
    }

    private static Prepared prepare(Set<PlotGeometry.Cell> cells) {
        PlotSelection.Bounds bounds = PlotPreviewGeometry.bounds(cells);
        List<PlotPreviewGeometry.Edge> edges = PlotPreviewGeometry.core(cells, bounds);
        List<PlotSelectionRegions.Region> regions = edges.size() == 12 && PlotPreviewGeometry.solid(cells, bounds)
                ? List.of(new PlotSelectionRegions.Region(bounds))
                : PlotSelectionRegions.decompose(new PlotSelectionShape(GEOMETRY_WORLD, null, cells));
        return new Prepared(bounds, new PlotEdgeIndex(edges), regions);
    }

    private void cancel(Entry entry) {
        cancel(entry.task);
    }

    void execute(FutureTask<?> task) {
        if (closed) throw new RejectedExecutionException("Plot preview worker is closed");
        executor.execute(task);
    }

    void cancel(FutureTask<?> task) {
        task.cancel(true);
        if (executor instanceof ThreadPoolExecutor pool) pool.remove(task);
    }

    @Override
    public synchronized void close() {
        closed = true;
        entries.values().forEach(this::cancel);
        entries.clear();
        if (executor instanceof ThreadPoolExecutor pool) {
            for (Runnable queued : pool.shutdownNow())
                if (queued instanceof FutureTask<?> task) task.cancel(true);
        }
    }

    private record Identity(Set<PlotGeometry.Cell> cells) {
        @Override public int hashCode() { return System.identityHashCode(cells); }
        @Override public boolean equals(Object other) { return other instanceof Identity key && cells == key.cells; }
    }

    private static final class Entry {
        private FutureTask<Void> task;
        private volatile Prepared result;
    }
}
