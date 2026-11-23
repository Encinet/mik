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

class PlotPrisonHostTest {
    @TempDir
    Path directory;

    @Test
    void togglingThePrisonFlagRoundTripsThroughTheRegistry() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("prison-flag.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotPrisonHost host = new PlotPrisonHost(registry);
            UUID world = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            UUID actor = UUID.randomUUID();
            Plot plot = new Plot(UUID.randomUUID(), world, owner, "Cell Block",
                    System.currentTimeMillis(), Set.of(Cell.at(0, 64, 0)), Map.of(), Map.of(), null);
            registry.put(plot);
            assertFalse(host.isEnabled(plot.id()));
            assertEquals(List.of(), host.enabled());

            host.setEnabled(plot.id(), true, actor, true);
            assertTrue(host.isEnabled(plot.id()));
            assertEquals(List.of(plot.id()), host.enabled().stream().map(Plot::id).toList());

            registry.load();
            assertTrue(host.isEnabled(plot.id()), "The flag must survive a registry reload");

            host.setEnabled(plot.id(), false, actor, true);
            assertFalse(host.isEnabled(plot.id()));
        }
    }

    @Test
    void enabledIsOrderedByNameThenId() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("prison-order.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotPrisonHost host = new PlotPrisonHost(registry);
            UUID world = UUID.randomUUID();
            UUID idA = UUID.randomUUID(), idB = UUID.randomUUID();
            Plot a = new Plot(idA, world, UUID.randomUUID(), "b",
                    0, Set.of(Cell.at(0, 64, 0)), Map.of(), Map.of(PrisonPlotHost.PRISON_ADDON_FLAG, true), null);
            Plot b = new Plot(idB, world, UUID.randomUUID(), "A",
                    0, Set.of(Cell.at(4, 64, 0)), Map.of(), Map.of(PrisonPlotHost.PRISON_ADDON_FLAG, true), null);
            registry.put(a);
            registry.put(b);
            List<Plot> enabled = host.enabled();
            assertEquals(2, enabled.size());
            assertEquals("A", enabled.get(0).name(), "Case-insensitive name order must come first");
        }
    }

    @Test
    void guardsVetoDisablingAnEnabledPlot() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("prison-guard.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotPrisonHost host = new PlotPrisonHost(registry);
            UUID world = UUID.randomUUID();
            UUID actor = UUID.randomUUID();
            Plot plot = new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Doom",
                    0, Set.of(Cell.at(0, 64, 0)), Map.of(),
                    Map.of(PrisonPlotHost.PRISON_ADDON_FLAG, true), null);
            registry.put(plot);
            host.addToggleGuard(_ -> false);
            assertFalse(host.mayToggleOff(plot), "A rejecting guard must veto toggling off");
            assertThrows(PlotProblem.class, () -> host.setEnabled(plot.id(), false, actor, true));
            assertTrue(host.isEnabled(plot.id()), "The flag must remain on while a guard vetoes the toggle");
        }
    }

    @Test
    void plotAtClimbsToTheFlaggedParent() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("prison-at.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotPrisonHost host = new PlotPrisonHost(registry);
            UUID world = UUID.randomUUID();
            UUID parentId = UUID.randomUUID();
            Plot parent = new Plot(parentId, world, UUID.randomUUID(), "Parent",
                    0, Set.of(Cell.at(0, 64, 0)), Map.of(),
                    Map.of(PrisonPlotHost.PRISON_ADDON_FLAG, true), null);
            registry.put(parent);
            registry.createSubPlot(parentId, UUID.randomUUID(), "Child",
                    PlotSelectionShape.of(new PlotPosition(world, 0, 64, 0), new PlotPosition(world, 3, 67, 3)));
            assertEquals(parent, host.plotAt(world, 0, 64, 0),
                    "A block in a sub-plot must resolve to the flagged parent");
        }
    }
}
