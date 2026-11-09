package org.encinet.mik.feature.world.plot;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlotDestinationTest {
    @TempDir Path directory;

    @Test void textUsesCodePointsAndPlainNamesAndDescriptionsHaveSeparateLimits() {
        UUID id = UUID.randomUUID(), plot = UUID.randomUUID();
        var point = new PlotDestination(id, plot, "😀".repeat(48), "😀".repeat(512), false, -.5, 65, -.5, 90, 0, "", "");
        assertEquals(-1, point.blockX());
        assertEquals(-1, point.blockZ());
        assertEquals(Message.PLOT_DESTINATION_ERROR_NAME, assertThrows(PlotProblem.class,
                () -> point.details("😀".repeat(49), "")).message());
        assertEquals(Message.PLOT_DESTINATION_ERROR_DESCRIPTION, assertThrows(PlotProblem.class,
                () -> point.details("Entrance", "😀".repeat(513))).message());
        assertEquals(Message.PLOT_DESTINATION_ERROR_NAME, assertThrows(PlotProblem.class,
                () -> point.details("first\nsecond", "")).message());
        assertThrows(PlotProblem.class, () -> point.details("Name", "text\u0000"));
        var literal = point.details("<gold>Entrance</gold>", "  First\r\nSecond\rThird  ");
        assertEquals("<gold>Entrance</gold>", literal.name());
        assertEquals("First\nSecond\nThird", literal.description());
        assertFalse(point.publicAccess());
    }

    @Test void multiplePointsHaveIndependentVisibilityAndSurviveRenameTransferReloadAndDeletion() throws Exception {
        Path database = directory.resolve("multiple.db");
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID(), successor = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), world, owner, "Park", 0,
                Set.of(new PlotGeometry.Cell(0, 16, 0), new PlotGeometry.Cell(1, 16, 0)), Map.of(), Map.of(), null);
        var publicPoint = new PlotDestination(UUID.randomUUID(), plot.id(), "Gate", "Welcome", true, 1, 65, 1, 0, 0, "", "");
        var privatePoint = new PlotDestination(UUID.randomUUID(), plot.id(), "Workshop", "Members", false, 5, 65, 1, 90, 0,
                "<gradient:gold:yellow>Workshop</gradient>", "<italic>Welcome</italic>");
        try (var store = new PlotRepository(database)) {
            store.open(); var registry = new PlotRegistry(store); registry.put(plot);
            registry.saveDestination(publicPoint); registry.saveDestination(privatePoint);
            assertEquals(List.of(publicPoint), registry.publicDestinations());
            assertEquals(2, registry.destinations(plot.id()).size());
            assertThrows(UnsupportedOperationException.class, () -> registry.destinations(plot.id()).clear());
            var edits = new PlotEdits(registry);
            edits.rename(plot, owner, false, "Renamed park"); edits.transfer(plot, owner, successor);
            registry.load();
            assertEquals(List.of(publicPoint, privatePoint), registry.destinations(plot.id()));
            assertEquals(successor, registry.byId(plot.id()).owner());
            registry.saveDestination(privatePoint.published(true));
            registry.deleteDestination(publicPoint.id());
            registry.load();
            assertEquals(List.of(privatePoint.published(true)), registry.publicDestinations());
            registry.remove(registry.byId(plot.id()));
            assertTrue(registry.publicDestinations().isEmpty());
            assertTrue(store.loadDestinations().isEmpty());
        }
        try (var store = new PlotRepository(database)) { store.open(); assertTrue(store.loadDestinations().isEmpty()); }
    }

    @Test void rangeAndSubplotValidationChecksEveryPointInsteadOfOnlyTheFirst() throws Exception {
        try (var store = new PlotRepository(directory.resolve("ranges.db"))) {
            store.open(); var registry = new PlotRegistry(store); var edits = new PlotEdits(registry);
            UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
            Plot plot = edits.create(owner, "Park", world, shape(world, 0, 1));
            var first = new PlotDestination(UUID.randomUUID(), plot.id(), "A", "", true, 1, 65, 1, 0, 0, "", "");
            var second = new PlotDestination(UUID.randomUUID(), plot.id(), "B", "", false, 5, 65, 1, 0, 0, "", "");
            registry.saveDestination(first); registry.saveDestination(second);
            assertEquals(Message.PLOT_ERROR_SUBPLOT_OWN_DESTINATION, assertThrows(PlotProblem.class,
                    () -> edits.validateResize(plot, shape(world, 0))).message());
            assertEquals(Message.PLOT_ERROR_SUBPLOT_DESTINATION, assertThrows(PlotProblem.class,
                    () -> registry.createSubPlot(plot.id(), owner, "Child", shape(world, 1))).message());
            registry.deleteDestination(second.id());
            assertDoesNotThrow(() -> edits.validateResize(plot, shape(world, 0)));
            registry.createSubPlot(plot.id(), owner, "Child", shape(world, 1));
            assertEquals(first, registry.destination(first.id()));
        }
    }

    private PlotSelectionShape shape(UUID world, int... xs) {
        return new PlotSelectionShape(world, null, java.util.Arrays.stream(xs)
                .mapToObj(x -> new PlotGeometry.Cell(x, 16, 0)).collect(java.util.stream.Collectors.toSet()));
    }
}
