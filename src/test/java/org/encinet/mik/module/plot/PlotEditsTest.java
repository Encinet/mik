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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotEditsTest {
    @TempDir Path directory;

    @Test
    void permissionClicksUseCurrentStateAndPersistCompleteCycles() throws Exception {
        Path database = directory.resolve("permission-cycles.db");
        UUID owner = UUID.randomUUID();
        Plot root = plot(UUID.randomUUID(), owner, Set.of(Cell.at(0, 64, 0)), true);
        UUID childId;
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(root);
            Plot child = registry.createSubPlot(root.id(), owner, "Room",
                    new PlotPosition(root.world(), 0, 64, 0),
                    new PlotPosition(root.world(), 3, 67, 3));
            childId = child.id();
            PlotEdits edits = new PlotEdits(registry);
            for (String action : List.of("door", "container", "entity_interact")) {
                for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values()) {
                    for (Plot original : List.of(root, child)) {
                        assertEquals(PlotAccessPolicy.Setting.ALLOW,
                                edits.cycleGroupAccess(original, owner, false, group, action));
                        assertEquals(Boolean.TRUE, registry.byId(original.id()).flags().get(group.key(action)));
                        assertEquals(PlotAccessPolicy.Setting.DENY,
                                edits.cycleGroupAccess(original, owner, false, group, action));
                        assertEquals(Boolean.FALSE, registry.byId(original.id()).flags().get(group.key(action)));
                        assertEquals(PlotAccessPolicy.Setting.DEFAULT,
                                edits.cycleGroupAccess(original, owner, false, group, action));
                        assertFalse(registry.byId(original.id()).flags().containsKey(group.key(action)));
                    }
                }
            }
            assertTrue(registry.byId(childId).flags().isEmpty());
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            assertTrue(registry.byId(childId).flags().isEmpty());
            assertTrue(registry.byId(root.id()).flags().isEmpty());
        }
    }

    @Test
    void permissionClicksRecheckRevokedAuthorityAndDeletedPlots() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("permission-authority.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID();
            UUID administrator = UUID.randomUUID();
            Plot root = new Plot(UUID.randomUUID(), UUID.randomUUID(), owner, "Home", false, 0,
                    Set.of(Cell.at(0, 64, 0)), Map.of(administrator, Plot.Role.ADMIN), Map.of(), null);
            registry.put(root);
            PlotEdits edits = new PlotEdits(registry);
            edits.removeMember(root, owner, false, administrator);
            assertEquals(Message.PLOT_ERROR_OWNER_ONLY, assertThrows(PlotProblem.class,
                    () -> edits.cycleGroupAccess(root, administrator, false,
                            PlotAccessPolicy.Group.COLLABORATOR, "container")).message());
            assertEquals(Message.PLOT_ERROR_FLAG, assertThrows(PlotProblem.class,
                    () -> edits.cycleGroupAccess(root, owner, false,
                            PlotAccessPolicy.Group.COLLABORATOR, "build")).message());
            assertTrue(registry.byId(root.id()).flags().isEmpty());
            registry.remove(registry.byId(root.id()));
            assertEquals(Message.PLOT_ERROR_ID, assertThrows(PlotProblem.class,
                    () -> edits.cycleGroupAccess(root, owner, false,
                            PlotAccessPolicy.Group.COLLABORATOR, "container")).message());
        }
    }

    @Test
    void ownershipAndEditingRulesApplyBeforeSaving() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("edits.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID(), guest = UUID.randomUUID();
            Plot plot = plot(UUID.randomUUID(), owner, Set.of(Cell.at(0, 64, 0)), false);
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            PlotProblem denied = assertThrows(PlotProblem.class,
                    () -> edits.managed(guest, false, plot.id().toString()));
            assertEquals(Message.PLOT_ERROR_OWNER_ONLY, denied.message());
            assertEquals(plot, edits.managed(guest, true, plot.id().toString()));

            edits.invite(plot, owner, false, guest, Plot.Role.COLLABORATOR);
            edits.flag(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, "member.container", true);
            edits.rename(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, "Shared workshop");
            Plot changed = registry.byId(plot.id());
            assertEquals(Plot.Role.COLLABORATOR, changed.members().get(guest));
            assertTrue(changed.flags().get("member.container"));
            assertFalse(changed.flags().containsKey("container"));
            assertEquals("Shared workshop", changed.name());
            assertFalse(changed.publicProject());

            edits.invite(changed, owner, false, guest, Plot.Role.ADMIN);
            assertEquals(Plot.Role.ADMIN, registry.byId(plot.id()).members().get(guest));
            edits.removeMember(registry.byId(plot.id()), owner, false, guest);
            assertFalse(registry.byId(plot.id()).members().containsKey(guest));
        }
    }

    @Test
    void renamingChangesOnlyTheDisplayNameAndKeepsTheStableProjectIdentity() throws Exception {
        Path database = directory.resolve("rename.db");
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID(), member = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Cell cell = Cell.at(0, 64, 0);
        Plot plot = new Plot(id, world, owner, "Old name", false, 123L,
                Set.of(cell), Map.of(member, Plot.Role.COLLABORATOR),
                Map.of("member.container", true), null);
        PlotArrival arrival = new PlotArrival(0.5, 65, 0.5, 90, 0,
                "<gold>Workshop</gold>", "Welcome");
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(plot);
            registry.setArrival(id, arrival);
            PlotEdits edits = new PlotEdits(registry);

            PlotProblem invalid = assertThrows(PlotProblem.class,
                    () -> edits.rename(plot, plot.owner(), false, "Broken\nname"));
            assertEquals(Message.PLOT_ERROR_NAME, invalid.message());
            assertEquals("Old name", registry.byId(id).name());

            edits.rename(plot, plot.owner(), false, "New name");
            Plot current = registry.byId(id);
            assertEquals(id, current.id());
            assertEquals("New name", current.name());
            assertEquals("New name", registry.at(world, 0, 64, 0).name());
            assertEquals(plot.cells(), current.cells());
            assertEquals(plot.members(), current.members());
            assertEquals(plot.flags(), current.flags());
            assertEquals(arrival, registry.arrival(id));
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            Plot persisted = registry.byId(id);
            assertEquals("New name", persisted.name());
            assertEquals(owner, persisted.owner());
            assertEquals(plot.cells(), persisted.cells());
            assertEquals(plot.members(), persisted.members());
            assertEquals(plot.flags(), persisted.flags());
            assertEquals(arrival, registry.arrival(id));
            registry.remove(persisted);
            assertEquals(null, registry.arrival(id));
            assertTrue(store.loadArrivals().isEmpty());
        }
    }

    @Test
    void localNoticeBoardPersistsThroughPlotEditsAndIsRemovedWithThePlot() throws Exception {
        Path database = directory.resolve("local-notice.db");
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID(), successor = UUID.randomUUID();
        Plot plot = plot(world, owner, Set.of(Cell.at(0, 64, 0)), false);
        String notice = "Welcome 🌿\n" + "Long garden announcement ".repeat(100)
                + "\nOpen garden\n".repeat(30) + "End";
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            assertEquals("", registry.noticeBoard(plot.id()));
            assertEquals(notice, edits.setNoticeBoard(plot, plot.owner(), false,
                    "  " + notice.replace("\n", "\r\n") + "  "));
            edits.rename(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, "Garden");
            edits.transfer(registry.byId(plot.id()), owner, successor);
            assertEquals(notice, registry.noticeBoard(plot.id()));
            assertEquals(plot.id(), registry.at(world, 0, 64, 0).id());
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            assertEquals(notice, registry.noticeBoard(plot.id()));
            assertEquals("Garden", registry.byId(plot.id()).name());
            assertEquals(successor, registry.byId(plot.id()).owner());

            PlotEdits edits = new PlotEdits(registry);
            assertEquals("", edits.setNoticeBoard(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, "  \n  "));
            assertEquals("", registry.noticeBoard(plot.id()));
            assertTrue(store.loadNoticeBoards().isEmpty());
            edits.setNoticeBoard(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, "Build in progress");
            registry.remove(registry.byId(plot.id()));
            assertTrue(store.loadNoticeBoards().isEmpty());
        }
    }

    @Test
    void localNoticeBoardRejectsOversizedOrControlText() {
        assertEquals("First\nSecond", PlotNoticeText.normalize("First\rSecond"));
        for (String invalid : new String[] {
                "x".repeat(PlotNoticeText.MAX_LENGTH + 1),
                "🌿".repeat(PlotNoticeText.MAX_LENGTH + 1), "tab\ttext", "bad\u0000text"
        }) {
            assertEquals(Message.PLOT_ERROR_NOTICE_TEXT,
                    assertThrows(PlotProblem.class,
                            () -> PlotNoticeText.normalize(invalid)).message());
        }
    }

    @Test
    void localAtmosphereSurvivesEditsAndReloadAndCascadesOnRelease() throws Exception {
        Path database = directory.resolve("atmosphere.db");
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID(), successor = UUID.randomUUID();
        Plot plot = plot(world, owner, Set.of(Cell.at(0, 64, 0)), false);
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            edits.setTime(plot, plot.owner(), false, PlotAtmosphere.parseClock("18:30"));
            edits.setWeather(plot, plot.owner(), false, PlotAtmosphere.Weather.RAIN);
            edits.rename(plot, plot.owner(), false, "Evening garden");
            edits.transfer(plot, owner, successor);
            assertEquals("18:30", PlotAtmosphere.clock(
                    registry.atmosphere(plot.id()).timeTicks()));
            assertEquals(PlotAtmosphere.Weather.RAIN,
                    registry.atmosphere(plot.id()).weather());
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            assertEquals("18:30", PlotAtmosphere.clock(
                    registry.atmosphere(plot.id()).timeTicks()));
            assertEquals(PlotAtmosphere.Weather.RAIN,
                    registry.atmosphere(plot.id()).weather());
            PlotEdits edits = new PlotEdits(registry);
            edits.setTime(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, null);
            assertEquals(null, registry.atmosphere(plot.id()).timeTicks());
            assertEquals(PlotAtmosphere.Weather.RAIN,
                    registry.atmosphere(plot.id()).weather());
            edits.setWeather(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, null);
            assertEquals(PlotAtmosphere.WORLD, registry.atmosphere(plot.id()));
            assertTrue(store.loadAtmospheres().isEmpty());
            edits.setTime(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, 6_000);
            registry.remove(registry.byId(plot.id()));
            assertTrue(store.loadAtmospheres().isEmpty());
        }
    }

    @Test
    void largePublicPlotCanBeMadePrivateAndPersistsItsWholeShape() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("size.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            Set<Cell> cells = new HashSet<>();
            for (int horizontalX = 0; horizontalX < 17; horizontalX++) {
                for (int horizontalZ = 0; horizontalZ < 17; horizontalZ++)
                    for (int verticalY = 16; verticalY < 32; verticalY++)
                        cells.add(new Cell(horizontalX, verticalY, horizontalZ));
            }
            Plot publicPlot = plot(UUID.randomUUID(), UUID.randomUUID(), cells, true);
            registry.put(publicPlot);
            edits.setPublic(publicPlot, publicPlot.owner(), false, false);
            assertFalse(registry.byId(publicPlot.id()).publicProject());
            assertEquals(cells, registry.byId(publicPlot.id()).cells());
            registry.load();
            assertFalse(registry.byId(publicPlot.id()).publicProject());
            assertEquals(cells, registry.byId(publicPlot.id()).cells());
        }
    }

    @Test
    void newcomerAndMemberDefaultsPersistAndPreserveExistingPermissions() throws Exception {
        Path database = directory.resolve("group-access.db");
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
        UUID collaborator = UUID.randomUUID(), visitor = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), world, owner, "Workshop", false, 0L,
                Set.of(Cell.at(0, 64, 0)),
                Map.of(collaborator, Plot.Role.COLLABORATOR, visitor, Plot.Role.COLLABORATOR),
                Map.of("newcomer.door", true, "member.door", true), null);
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(plot);
            UUID outsider = UUID.randomUUID();
            assertTrue(registry.allowed(world, 0, 64, 0, outsider, false, false, "door"));
            assertTrue(registry.allowed(world, 0, 64, 0, outsider, true, false, "door"));
            assertFalse(registry.allowed(world, 0, 64, 0, outsider, true, false, "build"));

            PlotEdits edits = new PlotEdits(registry);
            edits.setGroupAccess(plot, plot.owner(), false, PlotAccessPolicy.Group.MEMBER, "door", false);
            assertTrue(registry.allowed(world, 0, 64, 0, outsider, false, false, "door"));
            assertFalse(registry.allowed(world, 0, 64, 0, outsider, true, false, "door"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, true, false, "door"));
            assertTrue(registry.allowed(world, 0, 64, 0, collaborator, false, false, "build"));
            assertTrue(registry.allowed(world, 0, 64, 0, outsider, false, true, "build"));

            edits.flag(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, "member.container", true);
            edits.setGroupAccess(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, PlotAccessPolicy.Group.NEWCOMER,
                    "container", false);
            assertFalse(registry.allowed(world, 0, 64, 0, outsider, false, false, "container"));
            assertTrue(registry.allowed(world, 0, 64, 0, outsider, true, false, "container"));
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            UUID outsider = UUID.randomUUID();
            assertTrue(registry.allowed(world, 0, 64, 0, outsider, false, false, "door"));
            assertFalse(registry.allowed(world, 0, 64, 0, outsider, true, false, "door"));
            assertFalse(registry.allowed(world, 0, 64, 0, outsider, false, false, "container"));
            assertTrue(registry.allowed(world, 0, 64, 0, outsider, true, false, "container"));
        }
    }

    @Test
    void collaboratorOverridesCanBeSetAndClearedWithoutChangingOutsiderGroups() throws Exception {
        Path database = directory.resolve("collaborator-access.db");
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID(), outsider = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), world, owner, "Workshop", false, 0L,
                Set.of(Cell.at(0, 64, 0)), Map.of(visitor, Plot.Role.COLLABORATOR),
                Map.of("newcomer.door", false, "member.door", false,
                        "newcomer.container", false, "member.container", true), null);
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);

            assertTrue(registry.allowed(world, 0, 64, 0, visitor, false, false, "door"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, true, false, "door"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, false, false, "container"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, true, false, "container"));
            assertFalse(registry.allowed(world, 0, 64, 0, outsider, true, false, "door"));

            edits.setGroupAccess(plot, plot.owner(), false, PlotAccessPolicy.Group.COLLABORATOR, "door", false);
            edits.setGroupAccess(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false,
                    PlotAccessPolicy.Group.COLLABORATOR, "container", true);
            Plot changed = registry.byId(plot.id());
            assertFalse(registry.allowed(world, 0, 64, 0, visitor, true, false, "door"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, false, false, "container"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, true, false, "build"));
            assertFalse(registry.allowed(world, 0, 64, 0, outsider, false, false, "container"));
            edits.setGroupAccess(changed, changed.owner(), false, PlotAccessPolicy.Group.MEMBER, "container", null);
            assertFalse(registry.byId(plot.id()).flags().containsKey("member.container"));
            edits.setGroupAccess(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, PlotAccessPolicy.Group.MEMBER, "container", true);

            edits.setGroupAccess(changed, changed.owner(), false, PlotAccessPolicy.Group.COLLABORATOR, "container", null);
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, false, false, "container"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, true, false, "container"));
            edits.flag(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false, "collaborator.entity_interact", true);
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, false, false, "entity_interact"));
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            assertEquals(Map.of("collaborator.door", false, "collaborator.entity_interact", true,
                            "newcomer.door", false, "member.door", false,
                            "newcomer.container", false, "member.container", true),
                    registry.byId(plot.id()).flags());
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, false, false, "entity_interact"));
            assertTrue(registry.allowed(world, 0, 64, 0, visitor, false, false, "container"));
        }
    }

    @Test
    void adminsNeedPermissionManagementToGrantOrRemoveAdmins() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("admin.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID(), admin = UUID.randomUUID();
            UUID collaborator = UUID.randomUUID(), newcomer = UUID.randomUUID();
            Plot plot = plot(UUID.randomUUID(), owner, Set.of(Cell.at(0, 64, 0)), false);
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);

            edits.invite(plot, owner, false, admin, Plot.Role.ADMIN);
            Plot current = registry.byId(plot.id());
            assertTrue(current.canBuild(admin, false));
            assertEquals(current, edits.managed(admin, false, plot.id().toString()));
            edits.invite(current, admin, false, collaborator, Plot.Role.COLLABORATOR);
            edits.setGroupAccess(registry.byId(plot.id()), registry.byId(plot.id()).owner(), false,
                    PlotAccessPolicy.Group.NEWCOMER, "door", true);
            assertTrue(registry.byId(plot.id()).flags().get("newcomer.door"));

            edits.flag(plot, owner, false, "admin.manage_permissions", false);

            Plot latest = registry.byId(plot.id());
            assertEquals(Message.PLOT_ERROR_ADMIN_OWNER_ONLY,
                    assertThrows(PlotProblem.class, () -> edits.invite(latest, admin,
                            false, newcomer, Plot.Role.ADMIN)).message());
            assertEquals(Message.PLOT_ERROR_ADMIN_OWNER_ONLY,
                    assertThrows(PlotProblem.class, () -> edits.invite(latest, admin,
                            false, admin, Plot.Role.COLLABORATOR)).message());
            assertEquals(Message.PLOT_ERROR_ADMIN_OWNER_ONLY,
                    assertThrows(PlotProblem.class, () -> edits.removeMember(latest, admin,
                            false, admin)).message());
            assertEquals(Message.PLOT_ERROR_RELEASE_OWNER_ONLY,
                    assertThrows(PlotProblem.class, () -> edits.releasable(admin,
                            false, plot.id().toString())).message());
            assertEquals(Message.PLOT_ERROR_TRANSFER_OWNER_ONLY,
                    assertThrows(PlotProblem.class, () -> edits.transfer(latest, admin,
                            collaborator)).message());

            edits.invite(latest, owner, false, admin, Plot.Role.COLLABORATOR);
            assertFalse(registry.byId(plot.id()).canManage(admin, false));
            assertEquals(Message.PLOT_ERROR_OWNER_ONLY,
                    assertThrows(PlotProblem.class, () -> edits.managed(admin,
                            false, plot.id().toString())).message());
        }
    }

    @Test
    void ownershipTransferPersistsWithoutChangingProjectIdentityOrPresentation() throws Exception {
        Path database = directory.resolve("transfer.db");
        UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
        UUID successor = UUID.randomUUID(), collaborator = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PlotArrival arrival = new PlotArrival(0.5, 65, 0.5, 90, 0,
                "Workshop", "Welcome");
        Plot plot = new Plot(id, world, owner, "Workshop", true, 123L,
                Set.of(Cell.at(0, 64, 0)),
                Map.of(successor, Plot.Role.ADMIN, collaborator, Plot.Role.COLLABORATOR),
                Map.of("member.container", true), null);
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(plot);
            registry.setArrival(id, arrival);
            PlotEdits edits = new PlotEdits(registry);

            assertEquals(Message.PLOT_ERROR_TRANSFER_SELF,
                    assertThrows(PlotProblem.class, () -> edits.transfer(plot, owner,
                            owner)).message());
            edits.transfer(plot, owner, successor);
            Plot transferred = registry.byId(id);
            assertEquals(id, transferred.id());
            assertEquals(successor, transferred.owner());
            assertFalse(transferred.members().containsKey(successor));
            assertFalse(transferred.members().containsKey(owner));
            assertEquals(Plot.Role.COLLABORATOR, transferred.members().get(collaborator));
            assertFalse(registry.allowed(world, 0, 64, 0, owner, false, false, "build"));
            assertTrue(registry.allowed(world, 0, 64, 0, successor, false, false, "build"));
            assertEquals(arrival, registry.arrival(id));
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            Plot persisted = registry.byId(id);
            assertEquals(successor, persisted.owner());
            assertEquals(plot.cells(), persisted.cells());
            assertEquals(plot.flags(), persisted.flags());
            assertEquals(Plot.Role.COLLABORATOR, persisted.members().get(collaborator));
            assertEquals(arrival, registry.arrival(id));
        }
    }

    @Test
    void staleEditReferencesUseCurrentStateAndCurrentOwnership() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("stale-edit.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID(), successor = UUID.randomUUID();
            UUID admin = UUID.randomUUID();
            Plot original = plot(UUID.randomUUID(), owner,
                    Set.of(Cell.at(0, 64, 0)), false);
            registry.put(original);
            PlotEdits edits = new PlotEdits(registry);

            edits.invite(original, owner, false, admin, Plot.Role.ADMIN);
            edits.setGroupAccess(original, original.owner(), false, PlotAccessPolicy.Group.MEMBER,
                    "door", true);
            edits.transfer(original, owner, successor);

            Plot latest = registry.byId(original.id());
            assertEquals(successor, latest.owner());
            assertEquals(Plot.Role.ADMIN, latest.members().get(admin));
            assertTrue(latest.flags().get("member.door"));
            assertEquals(Message.PLOT_ERROR_OWNER_ONLY,
                    assertThrows(PlotProblem.class, () -> edits.invite(original, owner,
                            false, UUID.randomUUID(), Plot.Role.COLLABORATOR)).message());
        }
    }

    private static Plot plot(UUID world, UUID owner, Set<Cell> cells, boolean publicProject) {
        return new Plot(UUID.randomUUID(), world, owner, "Workshop", publicProject,
                0L, cells, Map.of(), Map.of(), null);
    }
}
