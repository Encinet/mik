package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotPreflightCacheTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();

    @Test
    void childPreflightDoesNotReuseParentResizeValidationForTheSameDraft() throws Exception {
        try (var store = new PlotRepository(directory.resolve("intent.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            Plot parent = edits.create(player, "Parent", world, cube(0, 11));
            edits.createSubPlot(parent, player, false, player, "Existing", cube(0, 3));
            ArrayDeque<Runnable> tasks = new ArrayDeque<>();
            var candidate = draft(cube(4, 7), 5);
            long revision = registry.revision();
            try (var worker = new PlotPreviewCache(tasks::add); var preflight = new PlotPreflightCache(worker)) {
                assertNull(preflight.request(player, world, parent, revision, candidate, edits));
                tasks.remove().run();
                assertEquals(Message.PLOT_ERROR_SUBPLOT_PARENT,
                        preflight.request(player, world, parent, revision, candidate, edits).failure().message());
                PlotEditorContext context = PlotEditorContext.subPlot(parent.id());
                assertNull(preflight.request(player, world, parent, context, revision, candidate, edits));
                tasks.remove().run();
                var result = preflight.request(player, world, parent, context, revision, candidate, edits);
                assertNull(result.failure());
                assertEquals(BigInteger.ONE, result.cells());
                assertEquals(1, registry.childrenOf(parent.id()).size());
                assertEquals(27, registry.byId(parent.id()).cells().size());
            }
        }
    }

    @Test
    void childPreflightRejectsOverlapOutsideParentAndNestedCreation() throws Exception {
        try (var store = new PlotRepository(directory.resolve("child-validation.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            Plot parent = edits.create(player, "Parent", world, cube(0, 7));
            Plot child = edits.createSubPlot(parent, player, false, player, "Existing", cube(0, 3));
            PlotEditorContext context = PlotEditorContext.subPlot(parent.id());
            try (var worker = new PlotPreviewCache(Runnable::run); var preflight = new PlotPreflightCache(worker)) {
                assertEquals(Message.PLOT_ERROR_SUBPLOT_OVERLAP, preflight.request(player, world, parent,
                        context, registry.revision(), draft(cube(0, 3), 1), edits).failure().message());
                assertEquals(Message.PLOT_ERROR_SUBPLOT_PARENT, preflight.request(player, world, parent,
                        context, registry.revision(), draft(cube(8, 11), 2), edits).failure().message());
                assertEquals(Message.PLOT_ERROR_SUBPLOT_DEPTH, preflight.request(player, world, child,
                        PlotEditorContext.subPlot(child.id()), registry.revision(), draft(cube(0, 3), 3), edits)
                        .failure().message());
                assertEquals(1, registry.childrenOf(parent.id()).size());
            }
        }
    }

    @Test
    void preflightUsesAnIsolatedSnapshotAndRevalidatesAfterARegistryChange() throws Exception {
        try (var store = new PlotRepository(directory.resolve("snapshot.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            ArrayDeque<Runnable> tasks = new ArrayDeque<>();
            try (var worker = new PlotPreviewCache(tasks::add); var preflight = new PlotPreflightCache(worker)) {
                var shape = cube(0, 3);
                var draft = draft(shape, 1);
                long original = registry.revision();
                assertNull(preflight.request(player, world, null, original, draft, edits));
                assertNull(preflight.request(player, world, null, original, draft, edits));
                assertEquals(1, tasks.size());
                edits.create(player, "Overlapping", world, shape);
                tasks.remove().run();
                var isolated = preflight.request(player, world, null, original, draft, edits);
                assertNull(isolated.failure());
                assertEquals(BigInteger.ONE, isolated.cells());
                assertNull(preflight.request(player, world, null, registry.revision(), draft, edits));
                tasks.remove().run();
                var current = preflight.request(player, world, null, registry.revision(), draft, edits);
                assertEquals(Message.PLOT_ERROR_OVERLAP, current.failure().message());
            }
        }
    }

    @Test
    void newerDraftCancelsTheOldJobAndReceivesOnlyItsOwnCellCount() throws Exception {
        try (var store = new PlotRepository(directory.resolve("draft.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            ArrayDeque<Runnable> tasks = new ArrayDeque<>();
            try (var worker = new PlotPreviewCache(tasks::add); var preflight = new PlotPreflightCache(worker)) {
                assertNull(preflight.request(player, world, null, 0, draft(cube(0, 3), 1), edits));
                Runnable outdated = tasks.remove();
                var current = draft(cube(0, 7), 2);
                assertNull(preflight.request(player, world, null, 0, current, edits));
                outdated.run();
                assertNull(preflight.request(player, world, null, 0, current, edits));
                tasks.remove().run();
                assertEquals(BigInteger.valueOf(8), preflight.request(player, world, null, 0, current, edits).cells());
            }
        }
    }

    @Test
    void enormousCuboidPreflightCountsAnalyticallyAndDoesNotChangeTheRegistry() throws Exception {
        try (var store = new PlotRepository(directory.resolve("huge.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            Plot plot = edits.create(player, "Small", world, cube(0, 3));
            try (var worker = new PlotPreviewCache(Runnable::run); var preflight = new PlotPreflightCache(worker)) {
                var result = preflight.request(player, world, plot, registry.revision(),
                        draft(cube(Integer.MIN_VALUE, Integer.MAX_VALUE), 1), edits);
                assertNull(result.failure());
                assertEquals(BigInteger.ONE.shiftLeft(90), result.cells());
                assertEquals(1, registry.byId(plot.id()).cells().size());
            }
        }
    }

    @Test
    void disconnectedCompositeStillFailsValidationWithoutAnyPersistence() throws Exception {
        try (var store = new PlotRepository(directory.resolve("disconnected.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            try (var worker = new PlotPreviewCache(Runnable::run); var preflight = new PlotPreflightCache(worker)) {
                var shape = new PlotSelectionShape(world, null, Set.of(new PlotGeometry.Cell(-3, 16, -3),
                        new PlotGeometry.Cell(3, 16, 3)));
                var result = preflight.request(player, world, null, registry.revision(), draft(shape, 1), edits);
                assertEquals(Message.PLOT_ERROR_AREA_DISCONNECTED, result.failure().message());
                assertTrue(registry.all().isEmpty());
                assertTrue(store.load().isEmpty());
            }
        }
    }

    @Test
    void aDraftInAnotherWorldIsRejectedWithoutQueuingValidation() throws Exception {
        try (var store = new PlotRepository(directory.resolve("world.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            Plot plot = edits.create(player, "Other world", world, cube(0, 3));
            ArrayDeque<Runnable> tasks = new ArrayDeque<>();
            try (var worker = new PlotPreviewCache(tasks::add); var preflight = new PlotPreflightCache(worker)) {
                var result = preflight.request(player, UUID.randomUUID(), plot, registry.revision(),
                        draft(cube(0, 3), 1), edits);
                assertEquals(Message.PLOT_ERROR_SELECTION, result.failure().message());
                assertTrue(tasks.isEmpty());
            }
        }
    }

    @Test
    void forgettingThePlayerAndShutdownCancelOutstandingValidation() throws Exception {
        try (var store = new PlotRepository(directory.resolve("closed.db"))) {
            store.open();
            var edits = new PlotEdits(new PlotRegistry(store));
            ArrayDeque<Runnable> tasks = new ArrayDeque<>();
            try (var worker = new PlotPreviewCache(tasks::add); var preflight = new PlotPreflightCache(worker)) {
                var draft = draft(cube(0, 3), 1);
                preflight.request(player, world, null, 0, draft, edits);
                Runnable forgotten = tasks.remove();
                preflight.forget(player);
                forgotten.run();
                assertNull(preflight.request(player, world, null, 0, draft, edits));
                preflight.close();
                tasks.remove().run();
                assertNull(preflight.request(player, world, null, 0, draft, edits));
            }
        }
    }

    @Test
    void areaStatisticsArePublishedWithTheSameCommittedSpatialSnapshot() throws Exception {
        try (var store = new PlotRepository(directory.resolve("area.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            Plot plot = edits.create(player, "Stats", world, cube(0, 7));
            assertEquals(64, registry.horizontalArea(plot));
            edits.rename(plot, plot.owner(), false, "Renamed");
            plot = registry.byId(plot.id());
            assertEquals(64, registry.horizontalArea(plot));
            edits.resize(plot, player, false, cube(0, 3));
            assertEquals(16, registry.horizontalArea(registry.byId(plot.id())));
            registry.load();
            assertEquals(16, registry.horizontalArea(registry.byId(plot.id())));
        }
    }

    private PlotSelectionShape cube(int minimum, int maximum) {
        return PlotSelectionShape.of(new PlotPosition(world, minimum, minimum, minimum),
                new PlotPosition(world, maximum, maximum, maximum));
    }

    private static PlotSelectionState.Draft draft(PlotSelectionShape shape, long revision) {
        return new PlotSelectionState.Draft(new PlotSelectionState.Base(new PlotSelectionState.Points(null, null), shape),
                java.util.List.of(), java.util.List.of(), revision);
    }
}
