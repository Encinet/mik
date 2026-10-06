package org.encinet.mik.module.plot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotDataPathsTest {
    @TempDir Path directory;

    @Test
    void allPlotPersistenceUsesOneDedicatedSubdirectory() {
        PlotDataPaths paths = PlotDataPaths.in(directory);
        assertEquals(directory.resolve("plots"), paths.directory());
        assertEquals(paths.directory().resolve("plots.db"), paths.plotsDatabase());
        assertEquals(paths.directory().resolve("board.db"), paths.boardDatabase());
        assertEquals(paths.directory().resolve("board-alerts.yml"), paths.boardAlerts());
        assertEquals(paths.directory(), paths.plotsDatabase().getParent());
        assertEquals(paths.directory(), paths.boardDatabase().getParent());
        assertEquals(paths.directory(), paths.boardAlerts().getParent());
    }

    @Test
    void openingTheCurrentStoreNeitherReadsNorMovesFilesFromThePluginRoot() throws Exception {
        Path rootDatabase = directory.resolve("plots.db");
        Files.writeString(rootDatabase, "root data is not a current database");
        PlotDataPaths paths = PlotDataPaths.in(directory);
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Home", false,
                0, Set.of(new PlotGeometry.Cell(-1, 16, -1)), Map.of(), Map.of(), null);
        try (PlotRepository store = new PlotRepository(paths.plotsDatabase())) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            assertTrue(registry.all().isEmpty());
            registry.put(plot);
        }
        try (PlotRepository store = new PlotRepository(paths.plotsDatabase())) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            assertEquals(plot, registry.byId(plot.id()));
        }
        assertTrue(Files.isRegularFile(paths.plotsDatabase()));
        assertEquals("root data is not a current database", Files.readString(rootDatabase));
        assertFalse(Files.exists(directory.resolve("plot-board.db")));
        assertFalse(Files.exists(directory.resolve("plot-board-alerts.yml")));
    }
}
