package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotSelectionValidationTest {
    @TempDir Path directory;
    private final UUID world = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @Test void resizeConfirmationCountsAnEnormousCuboidWithoutExpandingOrOverflowingIt() throws Exception {
        try (var store = new PlotRepository(directory.resolve("count.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            Plot plot = edits.create(owner, "Count", world,
                    PlotSelectionShape.of(new PlotPosition(world, 0, 64, 0), new PlotPosition(world, 3, 67, 3)));
            var enormous = PlotSelectionShape.of(new PlotPosition(world, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                    new PlotPosition(world, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
            assertEquals(BigInteger.ONE.shiftLeft(90),
                    assertTimeout(Duration.ofSeconds(1), () -> edits.resizedCellCount(plot, enormous)));
            assertEquals(1, registry.byId(plot.id()).cells().size());
        }
    }

    @Test void fullHeightPlotCanBeCreatedAndReloadedWithItsExactCells() throws Exception {
        try (var store = new PlotRepository(directory.resolve("full-height.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            var shape = PlotSelectionShape.of(new PlotPosition(world, 0, -64, 0), new PlotPosition(world, 127, 319, 127));
            Plot created = assertTimeout(Duration.ofSeconds(5), () -> edits.create(owner, "Full height", world, shape));
            assertEquals(98_304, created.cells().size());
            registry.load();
            assertEquals(created, registry.byId(created.id()));
            assertTrue(created.protects(0, -64, 0));
            assertTrue(created.protects(127, 319, 127));
            assertFalse(created.protects(128, 319, 127));
            assertFalse(created.protects(127, 320, 127));
        }
    }

    @Test void previewOfAnEnormousCuboidDoesNotEnumerateCellsOrOverflowItsVolume() {
        var registry = new PlotRegistry(null);
        var edits = new PlotEdits(registry);
        var shape = PlotSelectionShape.of(new PlotPosition(world, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                new PlotPosition(world, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTimeout(Duration.ofSeconds(1), () -> {
            edits.validateCreatable(world, shape);
            assertTrue(shape.intersects(Set.of(new Cell(0, 0, 0))));
            assertFalse(shape.intersects(Set.of(new Cell(Integer.MAX_VALUE, 0, 0))));
        });
        assertEquals(0, registry.revision());
        assertTrue(registry.all().isEmpty());
    }

    @Test void previewPreservesSelectionConnectivityAndOverlapErrorsWithoutWriting() throws Exception {
        try (var store = new PlotRepository(directory.resolve("validation.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            Plot existing = edits.create(owner, "Existing", world, shape(new Cell(0, 16, 0)));
            long revision = registry.revision();
            assertEquals(Message.PLOT_ERROR_OVERLAP, assertThrows(PlotProblem.class,
                    () -> edits.validateCreatable(world, shape(new Cell(0, 16, 0)))).message());
            assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                    () -> edits.validateCreatable(UUID.randomUUID(), shape(new Cell(2, 16, 0)))).message());
            assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                    () -> edits.validateCreatable(world, shape())).message());
            assertEquals(Message.PLOT_ERROR_AREA_DISCONNECTED, assertThrows(PlotProblem.class,
                    () -> edits.validateCreatable(world, shape(new Cell(2, 16, 0), new Cell(4, 16, 0)))).message());
            edits.validateResize(existing, shape(new Cell(0, 16, 0), new Cell(1, 16, 0)));
            edits.validateCreatable(world, shape(new Cell(2, 16, 0)));
            assertEquals(revision, registry.revision());
            assertEquals(Set.of(new Cell(0, 16, 0)), registry.byId(existing.id()).cells());
        }
    }

    @Test void previewResizeStillProtectsChildrenAndArrivalPoints() throws Exception {
        try (var store = new PlotRepository(directory.resolve("resize-validation.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            Plot parent = edits.create(owner, "Parent", world, shape(new Cell(0, 16, 0), new Cell(1, 16, 0)));
            registry.createSubPlot(parent.id(), UUID.randomUUID(), "Child", shape(new Cell(1, 16, 0)));
            registry.setArrival(parent.id(), new PlotArrival(0.5, 65, 0.5, 0, 0, "", ""));
            long revision = registry.revision();
            assertEquals(Message.PLOT_ERROR_SUBPLOT_PARENT, assertThrows(PlotProblem.class,
                    () -> edits.validateResize(parent, shape(new Cell(0, 16, 0)))).message());
            assertEquals(Message.PLOT_ERROR_SUBPLOT_OWN_ARRIVAL, assertThrows(PlotProblem.class,
                    () -> edits.validateResize(parent, shape(new Cell(1, 16, 0)))).message());
            edits.validateResize(parent, shape(new Cell(0, 16, 0), new Cell(1, 16, 0)));
            assertEquals(revision, registry.revision());
        }
    }

    @Test void overlapChecksKeepCourtyardsFreeAndIgnoreOtherWorlds() throws Exception {
        try (var store = new PlotRepository(directory.resolve("courtyard-validation.db"))) {
            store.open();
            var registry = new PlotRegistry(store);
            var edits = new PlotEdits(registry);
            PlotSelectionShape courtyard = PlotSelectionShape.of(new PlotPosition(world, 0, 64, 0), new PlotPosition(world, 11, 67, 11))
                    .apply(new PlotSelection(new PlotPosition(world, 4, 64, 4), new PlotPosition(world, 7, 67, 7)), PlotSelectionShape.Operation.SUBTRACT);
            edits.create(owner, "Courtyard", world, courtyard);
            edits.validateCreatable(world, shape(new Cell(1, 16, 1)));
            UUID otherWorld = UUID.randomUUID();
            edits.validateCreatable(otherWorld, PlotSelectionShape.of(new PlotPosition(otherWorld, 0, 64, 0), new PlotPosition(otherWorld, 11, 67, 11)));
            assertEquals(Message.PLOT_ERROR_OVERLAP, assertThrows(PlotProblem.class,
                    () -> edits.validateCreatable(world, shape(new Cell(0, 16, 0)))).message());
        }
    }

    private PlotSelectionShape shape(Cell... cells) { return PlotSelectionShape.fromCells(world, Set.of(cells)); }
}
