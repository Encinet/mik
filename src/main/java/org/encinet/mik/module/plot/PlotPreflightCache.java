package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

final class PlotPreflightCache implements AutoCloseable {
    private final PlotPreviewCache worker;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);
    private static final Outcome INCOMPLETE = new Outcome(null, new PlotProblem(Message.PLOT_ERROR_SELECTION));
    private static final Outcome EDITING = new Outcome(null, new PlotProblem(Message.PLOT_REGION_FINISH_FIRST));
    private boolean closed;

    record Outcome(BigInteger cells, PlotProblem failure) { }
    private record Key(UUID player, UUID world, PlotEditorContext context, long registry, long draft) { }

    PlotPreflightCache(PlotPreviewCache worker) { this.worker = worker; }

    Outcome request(UUID player, UUID world, Plot plot, long registryRevision,
                    PlotSelectionState.Draft draft, PlotEdits edits) {
        return request(player, world, plot, PlotEditorContext.area(plot == null ? null : plot.id()),
                registryRevision, draft, edits);
    }

    Outcome request(UUID player, UUID world, Plot plot, PlotEditorContext context, long registryRevision,
                    PlotSelectionState.Draft draft, PlotEdits edits) {
        if (closed) return null;
        if (draft.base().edit() != null) return EDITING;
        PlotSelectionShape shape = draft.base().candidate();
        if (shape == null || shape.empty() || !world.equals(shape.world())) return INCOMPLETE;
        if (!java.util.Objects.equals(context.plotId(), plot == null ? null : plot.id())) return INCOMPLETE;
        Key key = new Key(player, world, context, registryRevision, draft.revision());
        Entry previous = entries.get(key);
        if (previous != null) return previous.result;
        entries.entrySet().removeIf(entry -> {
            if (!entry.getKey().player().equals(player)) return false;
            worker.cancel(entry.getValue().task);
            return true;
        });
        if (entries.size() == PlotPreviewCache.CAPACITY) {
            var oldest = entries.entrySet().iterator();
            worker.cancel(oldest.next().getValue().task);
            oldest.remove();
        }
        PlotEdits snapshot = edits.validationSnapshot();
        Entry created = new Entry();
        created.task = new FutureTask<>(() -> {
            Outcome result;
            try {
                if (plot == null) {
                    snapshot.validateCreatable(world, shape);
                    result = new Outcome(shape.alignedCellCount(), null);
                } else result = new Outcome(context.subPlot() ? snapshot.subPlotCellCount(plot, shape)
                        : snapshot.resizedCellCount(plot, shape), null);
            } catch (PlotProblem problem) {
                result = new Outcome(null, problem);
            } catch (CancellationException cancelled) {
                return null;
            } catch (RuntimeException failure) {
                System.getLogger(PlotPreflightCache.class.getName()).log(System.Logger.Level.ERROR,
                        "Could not validate plot preview", failure);
                result = new Outcome(null, new PlotProblem(Message.PLOT_ERROR_STORAGE));
            }
            if (!Thread.currentThread().isInterrupted() && !created.task.isCancelled()) created.result = result;
            return null;
        });
        entries.put(key, created);
        try {
            worker.execute(created.task);
        } catch (RejectedExecutionException rejected) {
            entries.remove(key);
            worker.cancel(created.task);
        }
        return created.result;
    }

    void forget(UUID player) {
        entries.entrySet().removeIf(entry -> {
            if (!entry.getKey().player().equals(player)) return false;
            worker.cancel(entry.getValue().task);
            return true;
        });
    }

    @Override
    public void close() {
        closed = true;
        entries.values().forEach(entry -> worker.cancel(entry.task));
        entries.clear();
    }

    private static final class Entry {
        private FutureTask<Void> task;
        private volatile Outcome result;
    }
}
