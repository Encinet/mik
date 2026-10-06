package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotAreaEditsTest {
    @TempDir Path directory;
    private final UUID world = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @Test
    void creationUsesTheSelectedAreaWithoutEvidenceExpiryOrInvisibleMargins() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("create.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            PlotSelectionShape shape = PlotSelectionShape.of(new PlotPosition(world, -1, 65, 1),
                    new PlotPosition(world, 5, 66, 2));
            long revision = registry.revision();
            assertEquals(3, edits.creatableCells(world, shape).size());
            assertEquals(revision, registry.revision());
            Plot created = edits.create(owner, "Home", world, shape);
            assertEquals(shape.alignedCells(), created.cells());
            assertTrue(created.protects(-4, 64, 0));
            assertFalse(created.protects(-5, 64, 0));
            assertFalse(created.protects(8, 65, 1));
            registry.load();
            Plot persisted = registry.byId(created.id());
            assertEquals(created, persisted);
            assertNull(registry.at(world, 8, 65, 1));
            assertEquals(created.id(), registry.at(world, -4, 64, 0).id());
        }
    }

    @Test
    void overlappingCreationIsRejectedBeforeAnySave() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("overlap.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            edits.create(owner, "First", world, shape(new Cell(0, 16, 0)));
            long revision = registry.revision();
            assertEquals(Message.PLOT_ERROR_OVERLAP, assertThrows(PlotProblem.class,
                    () -> edits.create(UUID.randomUUID(), "Second", world, shape(new Cell(0, 16, 0)))).message());
            assertEquals(revision, registry.revision());
            assertEquals(1, registry.all().size());
        }
    }

    @Test
    void resizingPreservesIdentityPermissionsArrivalAndSettingsAcrossReload() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("resize.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            Plot original = edits.create(owner, "Home", world, shape(new Cell(0, 16, 0)));
            UUID collaborator = UUID.randomUUID();
            edits.invite(original, owner, false, collaborator, Plot.Role.COLLABORATOR);
            Plot before = registry.byId(original.id());
            registry.setNoticeBoard(before.id(), "Welcome");
            PlotArrival arrival = new PlotArrival(0.5, 65, 0.5, 90, 10, "Home", "Welcome");
            registry.setArrival(before.id(), arrival);
            registry.setAtmosphere(before.id(), new PlotAtmosphere(6000, PlotAtmosphere.Weather.CLEAR));
            edits.resize(before, owner, false, shape(new Cell(0, 16, 0), new Cell(1, 16, 0)));
            registry.load();
            Plot after = registry.byId(before.id());
            assertEquals(before.id(), after.id());
            assertEquals(before.owner(), after.owner());
            assertEquals(before.createdAt(), after.createdAt());
            assertEquals(before.members(), after.members());
            assertEquals(before.flags(), after.flags());
            assertEquals(2, after.cells().size());
            assertEquals(arrival, registry.arrival(after.id()));
            assertEquals("Welcome", registry.noticeBoard(after.id()));
            assertEquals(6000, registry.atmosphere(after.id()).timeTicks());
        }
    }

    @Test
    void shrinkingCannotDiscardChildrenOrTheArrivalPoint() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("shrink.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            Plot parent = edits.create(owner, "Town", world, shape(new Cell(0, 16, 0), new Cell(1, 16, 0)));
            registry.createSubPlot(parent.id(), UUID.randomUUID(), "Room", shape(new Cell(1, 16, 0)));
            assertEquals(Message.PLOT_ERROR_SUBPLOT_PARENT, assertThrows(PlotProblem.class,
                    () -> edits.resize(parent, owner, false, shape(new Cell(0, 16, 0)))).message());
            registry.setArrival(parent.id(), new PlotArrival(0.5, 65, 0.5, 0, 0, "", ""));
            assertEquals(Message.PLOT_ERROR_SUBPLOT_OWN_ARRIVAL, assertThrows(PlotProblem.class,
                    () -> edits.resize(parent, owner, false, shape(new Cell(1, 16, 0)))).message());
            assertEquals(parent.cells(), registry.byId(parent.id()).cells());
        }
    }

    @Test
    void confirmationsRecheckOwnershipAndRejectChangedBoundaries() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("stale.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            Plot original = edits.create(owner, "Home", world, shape(new Cell(0, 16, 0)));
            UUID successor = UUID.randomUUID();
            edits.transfer(original, owner, successor);
            assertEquals(Message.PLOT_ERROR_OWNER_ONLY, assertThrows(PlotProblem.class,
                    () -> edits.resize(original, owner, false, shape(new Cell(1, 16, 0)))).message());
            Plot captured = registry.byId(original.id());
            edits.resize(captured, successor, false, shape(new Cell(0, 16, 0), new Cell(1, 16, 0)));
            long revision = registry.revision();
            assertEquals(Message.PLOT_AREA_CHANGED, assertThrows(PlotProblem.class,
                    () -> edits.resize(captured, successor, false, shape(new Cell(2, 16, 0)))).message());
            assertEquals(revision, registry.revision());
            assertEquals(2, registry.byId(original.id()).cells().size());
        }
    }

    @Test
    void invalidSelectionsStillDoNotChangeRegistryWithoutSizeQuotas() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("invalid.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                    () -> edits.creatableCells(UUID.randomUUID(), shape(new Cell(0, 16, 0)))).message());
            assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                    () -> edits.creatableCells(world, shape())).message());
            assertEquals(Message.PLOT_ERROR_AREA_DISCONNECTED, assertThrows(PlotProblem.class,
                    () -> edits.creatableCells(world, shape(new Cell(0, 16, 0), new Cell(2, 16, 0)))).message());
            assertTrue(registry.all().isEmpty());
            assertEquals(0, registry.revision());
        }
    }

    @Test
    void ordinaryPlotsCanBeCreatedAndResizedPastAllFormerSizeQuotas() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("large.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            PlotSelectionShape initial = PlotSelectionShape.of(new PlotPosition(world, 0, 64, 0),
                    new PlotPosition(world, 67, 127, 67));
            Plot created = edits.create(owner, "Large building", world, initial);
            assertEquals(4624, created.cells().size());
            assertTrue(PlotGeometry.horizontalArea(created.cells()) > 4096);
            assertFalse(created.publicProject());

            PlotSelectionShape enlarged = PlotSelectionShape.of(new PlotPosition(world, 0, 64, 0),
                    new PlotPosition(world, 71, 131, 71));
            edits.resize(created, owner, false, enlarged);
            assertEquals(5508, registry.byId(created.id()).cells().size());
            registry.load();
            assertEquals(enlarged.alignedCells(), registry.byId(created.id()).cells());
            assertFalse(registry.byId(created.id()).publicProject());
        }
    }

    @Test
    void compositeCreationPreservesCourtyardHolesAndItsTrueOutline() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("courtyard.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Set<Cell> cells = new HashSet<>();
            for (int horizontalX = 0; horizontalX < 3; horizontalX++)
                for (int horizontalZ = 0; horizontalZ < 3; horizontalZ++)
                    if (horizontalX != 1 || horizontalZ != 1) cells.add(new Cell(horizontalX, 16, horizontalZ));
            Plot created = new PlotEdits(registry).create(owner, "Courtyard", world,
                    new PlotSelectionShape(world, null, cells));
            assertEquals(8, created.cells().size());
            assertFalse(created.protects(5, 65, 5));
            registry.load();
            assertFalse(registry.byId(created.id()).protects(5, 65, 5));
            assertNull(registry.at(world, 5, 65, 5));
        }
    }

    @Test
    void databaseFailureDoesNotPublishPartiallyChangedBoundaries() throws Exception {
        Path database = directory.resolve("atomic.db");
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            Plot plot = edits.create(owner, "Home", world, shape(new Cell(0, 16, 0)));
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_area BEFORE INSERT ON plot_cells BEGIN SELECT RAISE(ABORT, 'test'); END");
            }
            long revision = registry.revision();
            assertThrows(SQLException.class, () -> edits.resize(plot, owner, false,
                    shape(new Cell(1, 16, 0))));
            assertEquals(revision, registry.revision());
            assertEquals(plot, registry.byId(plot.id()));
            assertEquals(plot, registry.at(world, 0, 65, 0));
            registry.load();
            assertEquals(plot, registry.byId(plot.id()));
        }
    }

    @Test
    void freshDatabaseContainsOnlyCurrentPlotTables() throws Exception {
        Path database = PlotDataPaths.in(directory).plotsDatabase();
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            Plot plot = edits.create(owner, "Home", world, shape(new Cell(0, 16, 0)));
            registry.load();
            assertEquals(plot, registry.byId(plot.id()));
            assertNull(registry.at(world, 9, 65, 1));
            edits.create(UUID.randomUUID(), "Neighbor", world, shape(new Cell(1, 16, 0)));
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                Set<String> tables = new HashSet<>();
                while (rows.next()) tables.add(rows.getString(1));
                assertEquals(Set.of("plots", "plot_cells", "plot_members", "plot_flags",
                        "plot_arrivals", "plot_notice_boards", "plot_atmospheres", "plot_children"), tables);
            }
        }
    }

    private PlotSelectionShape shape(Cell... cells) {
        return new PlotSelectionShape(world, null, Set.of(cells));
    }
}
