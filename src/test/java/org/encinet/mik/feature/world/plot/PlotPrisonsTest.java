package org.encinet.mik.feature.world.plot;

import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotPrisonsTest {
    @TempDir
    Path directory;

    @Test
    void togglingThePrisonFlagRoundTripsThroughTheRegistry() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("prison-flag.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotPrisons host = new PlotPrisons(registry);
            UUID world = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            UUID actor = UUID.randomUUID();
            Plot plot = new Plot(UUID.randomUUID(), world, owner, "Cell Block",
                    System.currentTimeMillis(), Set.of(Cell.at(0, 64, 0)), Map.of(), Map.of(), null);
            registry.put(plot);
            assertTrue(host.regions().isEmpty());

            assertThrows(PlotProblem.class, () -> host.toggle(plot.id(), owner, false));
            host.toggle(plot.id(), actor, true);
            assertEquals(List.of(plot.id()), host.regions().stream().map(region -> region.plot().id()).toList());

            registry.load();
            assertEquals(List.of(plot.id()), host.regions().stream().map(region -> region.plot().id()).toList());

            host.toggle(plot.id(), actor, true);
            assertTrue(host.regions().isEmpty());
        }
    }

    @Test
    void catalogOrdersRegionsByStableId() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("prison-order.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotPrisons host = new PlotPrisons(registry);
            UUID world = UUID.randomUUID();
            UUID idA = new UUID(0, 1);
            UUID idB = new UUID(0, 2);
            Plot a = new Plot(idA, world, UUID.randomUUID(), "b",
                    0, Set.of(Cell.at(0, 64, 0)), Map.of(), Map.of(PlotPrisons.FLAG, true), null);
            Plot b = new Plot(idB, world, UUID.randomUUID(), "A",
                    0, Set.of(Cell.at(4, 64, 0)), Map.of(), Map.of(PlotPrisons.FLAG, true), null);
            registry.put(a);
            registry.put(b);
            assertEquals(List.of(idA, idB), host.regions().stream().map(region -> region.plot().id()).toList());
        }
    }

    @Test
    void guardsVetoDisablingAnEnabledPlot() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("prison-guard.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotPrisons host = new PlotPrisons(registry);
            UUID world = UUID.randomUUID();
            UUID actor = UUID.randomUUID();
            Plot plot = new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Doom",
                    0, Set.of(Cell.at(0, 64, 0)), Map.of(),
                    Map.of(PlotPrisons.FLAG, true), null);
            registry.put(plot);
            host.removalGuard(_ -> false);
            assertThrows(PlotProblem.class, () -> host.requireRemovable(plot));
            assertThrows(PlotProblem.class, () -> host.toggle(plot.id(), actor, true));
            assertEquals(List.of(plot.id()), host.regions().stream().map(region -> region.plot().id()).toList());
        }
    }

    @Test
    void plotAtClimbsToTheFlaggedParent() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("prison-at.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotPrisons host = new PlotPrisons(registry);
            UUID world = UUID.randomUUID();
            UUID parentId = UUID.randomUUID();
            Plot parent = new Plot(parentId, world, UUID.randomUUID(), "Parent",
                    0, Set.of(Cell.at(0, 64, 0)), Map.of(),
                    Map.of(PlotPrisons.FLAG, true), null);
            registry.put(parent);
            registry.createSubPlot(parentId, UUID.randomUUID(), "Child",
                    PlotSelectionShape.of(new PlotPosition(world, 0, 64, 0), new PlotPosition(world, 3, 67, 3)));
            assertEquals(parent, host.at(world, 0, 64, 0),
                    "A block in a sub-plot must resolve to the flagged parent");
        }
    }
}
