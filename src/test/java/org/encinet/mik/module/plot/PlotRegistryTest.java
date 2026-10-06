package org.encinet.mik.module.plot;

import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotRegistryTest {
    @TempDir Path directory;

    @Test
    void exactCellIndexHandlesNegativeChunkEdgesAndIntegerCoordinateExtremes() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("cell-index.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            for (int blockX : new int[]{Integer.MIN_VALUE, -17, -16, -1, 0, 15, 16, Integer.MAX_VALUE}) {
                UUID world = UUID.randomUUID();
                Cell cell = Cell.at(blockX, 64, blockX);
                Plot plot = new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Home", false,
                        0, Set.of(cell), Map.of(), Map.of(), null);
                registry.put(plot);
                int minimum = cell.x() * PlotGeometry.CELL;
                int maximum = minimum + PlotGeometry.CELL - 1;
                assertEquals(plot, registry.at(world, minimum, 64, minimum));
                assertEquals(plot, registry.at(world, maximum, 67, maximum));
                int outside = maximum == Integer.MAX_VALUE ? minimum - 1 : maximum + 1;
                assertEquals(null, registry.at(world, outside, 64, blockX));
            }
            registry.load();
            for (Plot plot : registry.all()) {
                Cell cell = plot.cells().iterator().next();
                assertEquals(plot, registry.at(plot.world(), cell.x() * PlotGeometry.CELL,
                        cell.y() * PlotGeometry.CELL, cell.z() * PlotGeometry.CELL));
            }
        }
    }

    @Test
    void exactBoundariesAndPermissionRolesArePersisted() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("permissions.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
            UUID collaborator = UUID.randomUUID(), visitor = UUID.randomUUID();
            Map<UUID, Plot.Role> members = new HashMap<>();
            members.put(collaborator, Plot.Role.COLLABORATOR);
            members.put(visitor, Plot.Role.COLLABORATOR);
            Plot plot = new Plot(UUID.randomUUID(), world, owner, "Hall", false,
                    System.currentTimeMillis(), Set.of(Cell.at(0, 64, 0)), members,
                    Map.of("newcomer.door", true, "member.door", true,
                            PlotAccessPolicy.playerKey(visitor, "place"), false,
                            PlotAccessPolicy.playerKey(visitor, "container"), false), null);
            registry.put(plot);
            assertEquals(null, registry.at(world, 8, 64, 1));
            assertFalse(plot.protects(8, 64, 1));
            assertNotNull(registry.at(world, 0, 64, 0));
            assertEquals(null, registry.at(world, -1, 64, 0));
            assertTrue(registry.allowed(world, 0, 64, 1, collaborator, false, false, "build"));
            assertFalse(registry.allowed(world, 0, 64, 1, visitor, false, false, "build"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, false, false, "door"));
            assertFalse(registry.allowed(world, 0, 64, 0, visitor, false, false, "container"));
            assertTrue(registry.allowed(world, 0, 64, 0, UUID.randomUUID(), false, true, "build"));
        }
    }

    @Test
    void separateOwnersCanShareABoundaryButCannotOverlapTheSameCell() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("overlap.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID world = UUID.randomUUID();
            Plot first = new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "One", false,
                    0, Set.of(new Cell(0, 16, 0)), Map.of(), Map.of(), null);
            registry.put(first);
            Plot near = new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Two", false,
                    0, Set.of(new Cell(2, 16, 0)), Map.of(), Map.of(), null);
            Plot far = new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Three", false,
                    0, Set.of(new Cell(3, 16, 0)), Map.of(), Map.of(), null);
            Plot overlapping = new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Four", false,
                    0, Set.of(new Cell(0, 16, 0)), Map.of(), Map.of(), null);
            assertTrue(registry.overlapsOther(overlapping));
            assertFalse(registry.overlapsOther(near));
            assertFalse(registry.overlapsOther(far));
        }
    }

    @Test
    void spatialReadsUseTheLatestMetadataWithoutChangingCoverage() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("snapshot.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
            Plot plot = new Plot(UUID.randomUUID(), world, owner, "Old", false,
                    0, Set.of(Cell.at(0, 64, 0)), Map.of(), Map.of(), null);
            registry.put(plot);
            UUID visitor = UUID.randomUUID();
            assertFalse(registry.allowed(world, 0, 64, 0, visitor,
                    false, false, "door"));

            new PlotEdits(registry).flag(plot, plot.owner(), false, "newcomer.door", true);
            registry.rename(plot.id(), "New");

            assertTrue(registry.allowed(world, 0, 64, 0, visitor,
                    false, false, "door"));
            assertEquals("New", registry.at(world, 0, 64, 0).name());
        }
    }

    @Test
    void reloadReplacesDeletedPlotsAndTheirPresentationState() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("reload.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
            Plot plot = new Plot(UUID.randomUUID(), world, owner, "Home", false,
                    0, Set.of(Cell.at(0, 64, 0)), Map.of(), Map.of(), null);
            registry.put(plot);
            registry.setArrival(plot.id(), new PlotArrival(0, 64, 0, 0, 0, "", ""));
            registry.setNoticeBoard(plot.id(), "Welcome");
            store.delete(plot.id());

            registry.load();

            assertTrue(registry.all().isEmpty());
            assertEquals(null, registry.at(world, 0, 64, 0));
            assertEquals(null, registry.arrival(plot.id()));
            assertEquals("", registry.noticeBoard(plot.id()));
        }
    }

}
