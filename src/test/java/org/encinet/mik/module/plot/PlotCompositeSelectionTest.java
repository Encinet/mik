package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotCompositeSelectionTest {
    @TempDir Path directory;
    private final UUID world = UUID.randomUUID();

    @Test
    void courtyardCreationAndResizePersistExactShapeAndKeepProjectIdentity() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("courtyard.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = plot(grid(3, 16));
            registry.put(parent);
            PlotEdits edits = new PlotEdits(registry);
            PlotSelectionShape courtyard = courtyard(16);
            Plot child = edits.createSubPlot(parent, parent.owner(), false, UUID.randomUUID(), "Courtyard", courtyard);
            assertEquals(courtyard.cells(), child.cells());
            assertFalse(child.protects(5, 65, 5));
            registry.load();
            assertEquals(courtyard.cells(), registry.byId(child.id()).cells());
            Set<Cell> resized = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(0, 16, 1));
            edits.resizeSubPlot(child, parent.owner(), false, shape(resized));
            registry.load();
            assertEquals(resized, registry.byId(child.id()).cells());
            assertEquals(child.owner(), registry.byId(child.id()).owner());
            assertEquals(parent.id(), registry.byId(child.id()).parentId());
            assertFalse(registry.byId(child.id()).protects(5, 65, 5));
        }
    }

    @Test
    void compositeValidationRejectsForeignCellsDisconnectionsAndSiblingOverlap() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("validation.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = plot(grid(3, 16));
            registry.put(parent);
            assertEquals(Message.PLOT_ERROR_SUBPLOT_PARENT, assertThrows(PlotProblem.class,
                    () -> registry.creatableSubPlotCells(parent, shape(Set.of(new Cell(3, 16, 0))))).message());
            assertEquals(Message.PLOT_ERROR_SUBPLOT_DISCONNECTED, assertThrows(PlotProblem.class,
                    () -> registry.creatableSubPlotCells(parent, shape(Set.of(new Cell(0, 16, 0), new Cell(2, 16, 2))))).message());
            registry.createSubPlot(parent.id(), UUID.randomUUID(), "Existing", shape(Set.of(new Cell(0, 16, 0))));
            assertEquals(Message.PLOT_ERROR_SUBPLOT_OVERLAP, assertThrows(PlotProblem.class,
                    () -> registry.creatableSubPlotCells(parent, courtyard(16))).message());
            assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                    () -> registry.creatableSubPlotCells(parent,
                            new PlotSelectionShape(UUID.randomUUID(), null, Set.of(new Cell(1, 16, 0))))).message());
        }
    }

    @Test
    void subtractingArrivalsIsValidatedAgainstRealCellsNotTheirEnvelope() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("arrival.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = plot(grid(3, 16));
            registry.put(parent);
            registry.setArrival(parent.id(), new PlotArrival(5, 64, 5, 0, 0, "", ""));
            Plot child = registry.createSubPlot(parent.id(), UUID.randomUUID(), "Ring", courtyard(16));
            registry.setArrival(child.id(), new PlotArrival(1, 64, 1, 0, 0, "", ""));
            assertEquals(Message.PLOT_ERROR_SUBPLOT_ARRIVAL, assertThrows(PlotProblem.class,
                    () -> registry.resizedSubPlotCells(child, shape(parent.cells()))).message());
            Set<Cell> removed = new HashSet<>(child.cells());
            removed.remove(new Cell(0, 16, 0));
            assertEquals(Message.PLOT_ERROR_SUBPLOT_OWN_ARRIVAL, assertThrows(PlotProblem.class,
                    () -> registry.resizedSubPlotCells(child, shape(removed))).message());
            assertEquals(8, registry.byId(child.id()).cells().size());
        }
    }

    @Test
    void previewUsesRequestedProjectDraftAndBlocksPendingBrush() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("preview.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = plot(grid(3, 16));
            registry.put(parent);
            PlotEdits edits = new PlotEdits(registry);
            PlotSelectionState selections = new PlotSelectionState();
            UUID actor = parent.owner();
            selections.bind(actor, parent.id(), world);
            selections.fit(actor, world, courtyard(16).cells());
            selections.bind(actor, UUID.randomUUID(), world);
            selections.fit(actor, world, Set.of(new Cell(20, 16, 20)));
            PlotSelectionShape selected = selections.submission(actor, parent.id(), world).shape();
            assertEquals(8, edits.resizedCells(parent, selected).size());
            selections.bind(actor, parent.id(), world);
            selections.mark(actor, true, new PlotPosition(world, 0, 64, 0));
            assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                    () -> selections.submission(actor, parent.id(), world)).message());
            assertTrue(registry.childrenOf(parent.id()).isEmpty());
        }
    }

    @Test
    void compositeSubmissionsRecheckCurrentPermissions() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("permission.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = plot(grid(3, 16));
            registry.put(parent);
            PlotEdits edits = new PlotEdits(registry);
            assertEquals(Message.PLOT_ERROR_SUBPLOT_OWNER, assertThrows(PlotProblem.class,
                    () -> edits.createSubPlot(parent, UUID.randomUUID(), false, UUID.randomUUID(), "Denied", courtyard(16))).message());
            assertTrue(registry.childrenOf(parent.id()).isEmpty());
        }
    }

    private Plot plot(Set<Cell> cells) {
        return new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Town", false,
                System.currentTimeMillis(), cells, Map.of(), Map.of(), null);
    }

    private Set<Cell> grid(int size, int cellY) {
        Set<Cell> cells = new HashSet<>();
        for (int cellX = 0; cellX < size; cellX++)
            for (int cellZ = 0; cellZ < size; cellZ++) cells.add(new Cell(cellX, cellY, cellZ));
        return Set.copyOf(cells);
    }

    private PlotSelectionShape courtyard(int cellY) {
        Set<Cell> cells = new HashSet<>(grid(3, cellY));
        cells.remove(new Cell(1, cellY, 1));
        return shape(cells);
    }

    private PlotSelectionShape shape(Set<Cell> cells) {
        return new PlotSelectionShape(world, null, cells);
    }
}
