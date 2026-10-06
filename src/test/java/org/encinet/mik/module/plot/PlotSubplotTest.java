package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotSubplotTest {
    @TempDir Path directory;

    @Test
    void childCreationPermissionIsInapplicableEvenToOwnersAndSupervisors() throws Exception {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID childOwner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        try (PlotRepository store = new PlotRepository(directory.resolve("applicable.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = parent(world, owner, Map.of());
            registry.put(parent);
            PlotEdits edits = new PlotEdits(registry);
            Plot created = edits.createSubPlot(parent, owner, false, childOwner, "Child",
                    point(world, 0, 64, 0), point(world, 3, 67, 3));
            Plot child = new Plot(created.id(), world, childOwner, created.name(), false, created.createdAt(),
                    created.cells(), Map.of(member, Plot.Role.COLLABORATOR),
                    Map.of(PlotAccessPolicy.playerKey(member, "create_subplot"), true), parent.id());
            registry.put(child);
            assertTrue(PlotPermission.CREATE_SUBPLOT.appliesTo(parent));
            assertFalse(PlotPermission.CREATE_SUBPLOT.appliesTo(child));
            for (UUID actor : java.util.List.of(owner, childOwner, member)) {
                assertFalse(registry.permitted(child, actor, false, PlotPermission.CREATE_SUBPLOT));
                assertFalse(registry.permitted(child, actor, true, PlotPermission.CREATE_SUBPLOT));
            }
            assertFalse(registry.canManage(child, member, false));
            for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values())
                assertFalse(PlotAccessPolicy.view(child, parent, group, "create_subplot").allowed());
            assertProblem(Message.PLOT_ERROR_FLAG, () -> edits.setGroupAccess(child, owner, false,
                    PlotAccessPolicy.Group.OWNER, "create_subplot", true));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_DEPTH, () -> edits.createSubPlot(child, owner, true,
                    childOwner, "Nested", point(world, 0, 64, 0), point(world, 3, 67, 3)));
        }
    }

    @Test
    void delegatedSubplotCreationDoesNotGrantParentOrChildAreaEditingAndRechecksRevocation() throws Exception {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        UUID childOwner = UUID.randomUUID();
        try (PlotRepository store = new PlotRepository(directory.resolve("create-permission.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = parent(world, owner, Map.of(creator, Plot.Role.COLLABORATOR));
            registry.put(parent);
            PlotEdits edits = new PlotEdits(registry);
            edits.cycleAccess(parent, owner, false, PlotAccessPolicy.Subject.player(creator), "create_subplot");
            Plot child = edits.createSubPlot(parent, creator, false, childOwner, "Child",
                    point(world, 0, 64, 0), point(world, 3, 67, 3));
            assertEquals(childOwner, child.owner());
            assertEquals(parent.cells(), registry.byId(parent.id()).cells());
            assertFalse(registry.permitted(registry.byId(parent.id()), creator, false, PlotPermission.MANAGE_AREA));
            assertProblem(Message.PLOT_ERROR_OWNER_ONLY, () -> edits.resize(parent, creator, false,
                    PlotSelectionShape.of(point(world, 0, 64, 0), point(world, 11, 67, 3))));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_OWNER, () -> edits.resizeSubPlot(child, creator, false,
                    point(world, 0, 64, 0), point(world, 3, 67, 3)));
            edits.cycleAccess(parent, owner, false, PlotAccessPolicy.Subject.player(creator), "create_subplot");
            assertProblem(Message.PLOT_ERROR_SUBPLOT_OWNER, () -> edits.createSubPlot(parent, creator, false,
                    childOwner, "Denied", point(world, 4, 64, 0), point(world, 7, 67, 3)));
            assertEquals(1, registry.childrenOf(parent.id()).size());
        }
    }

    @Test
    void areaManagementDoesNotImplicitlyGrantSubplotCreation() throws Exception {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID editor = UUID.randomUUID();
        try (PlotRepository store = new PlotRepository(directory.resolve("area-permission.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = parent(world, owner, Map.of(editor, Plot.Role.COLLABORATOR));
            registry.put(parent);
            PlotEdits edits = new PlotEdits(registry);
            edits.cycleAccess(parent, owner, false, PlotAccessPolicy.Subject.player(editor), "manage_area");
            assertTrue(registry.permitted(registry.byId(parent.id()), editor, false, PlotPermission.MANAGE_AREA));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_OWNER, () -> edits.createSubPlot(parent, editor, false,
                    editor, "Denied", point(world, 0, 64, 0), point(world, 3, 67, 3)));
            edits.flag(parent, owner, false, "owner.create_subplot", false);
            assertProblem(Message.PLOT_ERROR_SUBPLOT_OWNER, () -> edits.createSubPlot(parent, owner, false,
                    owner, "Owner denied", point(world, 0, 64, 0), point(world, 3, 67, 3)));
            assertTrue(registry.permitted(registry.byId(parent.id()), owner, false, PlotPermission.MANAGE_AREA));
            assertTrue(registry.childrenOf(parent.id()).isEmpty());
        }
    }

    @Test
    void childUsesOnlyParentCellsAndOwnPermissionsWhileParentOwnerKeepsOversight()
            throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("permissions.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID world = UUID.randomUUID(), parentOwner = UUID.randomUUID();
            UUID childOwner = UUID.randomUUID(), parentBuilder = UUID.randomUUID();
            Plot parent = parent(world, parentOwner, Map.of(parentBuilder,
                    Plot.Role.COLLABORATOR));
            registry.put(parent);
            Plot child = registry.createSubPlot(parent.id(), childOwner, "East town",
                    point(world, 0, 64, 0), point(world, 7, 67, 3));

            assertEquals(parent.id(), child.parentId());
            assertEquals(Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0)), child.cells());
            assertEquals(child.id(), registry.at(world, 2, 64, 0).id());
            assertEquals(parent.id(), registry.at(world, 8, 64, 0).id());
            assertTrue(registry.allowed(world, 2, 64, 0, childOwner, false, false, "build"));
            assertTrue(registry.allowed(world, 2, 64, 0, parentOwner, false, false, "build"));
            assertFalse(registry.allowed(world, 2, 64, 0, parentBuilder, false, false, "build"));
            assertFalse(registry.allowed(world, 8, 64, 0, childOwner, false, false, "build"));
            assertTrue(registry.allowed(world, 8, 64, 0, parentBuilder, false, false, "build"));
            assertTrue(registry.canManage(child, parentOwner, false));
            assertFalse(registry.canManage(parent, childOwner, false));
            assertEquals(Map.of(), child.flags());
            assertEquals(PlotRegistry.Standing.PARENT_OWNER,
                    registry.standing(child, parentOwner, false));
            assertEquals(PlotRegistry.Standing.PARENT_OWNER,
                    registry.standing(child, parentOwner, true));
            assertEquals(PlotRegistry.Standing.OWNER,
                    registry.standing(child, childOwner, true));
            assertEquals(PlotRegistry.Standing.OUTSIDER,
                    registry.standing(child, parentBuilder, false));
        }
    }

    @Test
    void childDefaultsTrackParentUntilOverriddenAndMembersStaySeparate() throws Exception {
        Path db = directory.resolve("inherited-access.db");
        UUID world = UUID.randomUUID(), parentOwner = UUID.randomUUID();
        UUID childOwner = UUID.randomUUID(), visitor = UUID.randomUUID();
        UUID parentId, childId;
        try (PlotRepository store = new PlotRepository(db)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            Plot parent = new Plot(UUID.randomUUID(), world, parentOwner, "Town", true, 0L,
                    Set.of(new Cell(0, 16, 0)), Map.of(visitor, Plot.Role.ADMIN),
                    Map.of("newcomer.container", true, "collaborator.entity_interact", true), null);
            registry.put(parent);
            Plot child = registry.createSubPlot(parent.id(), childOwner, "East",
                    point(world, 0, 64, 0), point(world, 3, 64, 3));
            parentId = parent.id();
            childId = child.id();
            UUID newcomer = UUID.randomUUID();
            assertTrue(registry.allowed(world, 0, 64, 0, newcomer,
                    false, false, "container"));
            assertFalse(registry.allowed(world, 0, 64, 0, visitor,
                    false, false, "build"));
            assertEquals(PlotRegistry.Standing.OUTSIDER,
                    registry.standing(child, visitor, false));
            assertFalse(registry.canManage(child, visitor, false));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_SUPERVISOR_MEMBER,
                    () -> edits.invite(child, childOwner, false,
                            parentOwner, Plot.Role.COLLABORATOR));

            edits.setGroupAccess(parent, parent.owner(), false, PlotAccessPolicy.Group.NEWCOMER,
                    "container", false);
            assertFalse(registry.allowed(world, 0, 64, 0, newcomer,
                    false, false, "container"));
            edits.setGroupAccess(child, child.owner(), false, PlotAccessPolicy.Group.NEWCOMER,
                    "container", true);
            assertTrue(registry.allowed(world, 0, 64, 0, newcomer,
                    false, false, "container"));
            edits.setGroupAccess(child, child.owner(), false, PlotAccessPolicy.Group.NEWCOMER,
                    "container", null);
            assertFalse(registry.allowed(world, 0, 64, 0, newcomer,
                    false, false, "container"));

            edits.invite(child, childOwner, false, visitor, Plot.Role.COLLABORATOR);
            assertTrue(registry.allowed(world, 0, 64, 0, visitor,
                    false, false, "entity_interact"));
            edits.setGroupAccess(child, child.owner(), false, PlotAccessPolicy.Group.COLLABORATOR, "entity_interact", false);
            assertFalse(registry.allowed(world, 0, 64, 0, visitor,
                    false, false, "entity_interact"));
            edits.setGroupAccess(child, child.owner(), false, PlotAccessPolicy.Group.COLLABORATOR, "entity_interact", null);
            assertTrue(registry.allowed(world, 0, 64, 0, visitor,
                    false, false, "entity_interact"));
        }
        try (PlotRepository store = new PlotRepository(db)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            assertEquals(parentId, registry.byId(childId).parentId());
            assertEquals(Map.of(), registry.byId(childId).flags());
            assertTrue(registry.allowed(world, 0, 64, 0, visitor,
                    false, false, "entity_interact"));
        }
    }

    @Test
    void resettingChildAccessRestoresParentDefaultsWithoutClearingOtherSettings()
            throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("reset-access.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            UUID world = UUID.randomUUID(), parentOwner = UUID.randomUUID();
            UUID childOwner = UUID.randomUUID(), newcomer = UUID.randomUUID();
            Plot parent = new Plot(UUID.randomUUID(), world, parentOwner, "Town", true, 0L,
                    Set.of(new Cell(0, 16, 0)), Map.of(),
                    Map.of("newcomer.container", false), null);
            registry.put(parent);
            Plot child = registry.createSubPlot(parent.id(), childOwner, "East",
                    point(world, 0, 64, 0), point(world, 3, 64, 3));
            registry.put(new Plot(child.id(), child.world(), child.owner(), child.name(),
                    child.publicProject(), child.createdAt(), child.cells(), child.members(),
                    Map.of("newcomer.container", true, "collaborator.entity_interact", false,
                            "member.door", true, "future.option", true), child.parentId()));
            assertTrue(registry.allowed(world, 0, 64, 0, newcomer,
                    false, false, "container"));

            edits.resetAccessToParent(child, child.owner(), false);
            assertEquals(Map.of("future.option", true), registry.byId(child.id()).flags());
            assertFalse(registry.allowed(world, 0, 64, 0, newcomer,
                    false, false, "container"));
            edits.setGroupAccess(parent, parent.owner(), false, PlotAccessPolicy.Group.NEWCOMER,
                    "container", true);
            assertTrue(registry.allowed(world, 0, 64, 0, newcomer,
                    false, false, "container"));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_PARENT,
                    () -> edits.resetAccessToParent(parent, parent.owner(), false));
        }
    }

    @Test
    void selectionRejectsOverlapDisconnectedCellsAndParentArrival() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("selection.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
            Plot parent = parent(world, owner, Map.of());
            registry.put(parent);
            registry.createSubPlot(parent.id(), owner, "East",
                    point(world, 0, 64, 0), point(world, 3, 64, 0));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_OVERLAP,
                    () -> registry.createSubPlot(parent.id(), owner, "Overlap",
                            point(world, 0, 64, 0), point(world, 7, 64, 0)));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_PARENT,
                    () -> registry.createSubPlot(parent.id(), owner, "Outside",
                            point(world, 8, 64, 0), point(world, 12, 64, 0)));

            registry.setArrival(parent.id(), new PlotArrival(9, 64, 1, 0, 0, "", ""));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_ARRIVAL,
                    () -> registry.createSubPlot(parent.id(), owner, "Portal",
                            point(world, 8, 64, 0), point(world, 11, 64, 3)));

            Plot disconnected = new Plot(UUID.randomUUID(), world, owner, "Islands", true,
                    0L, Set.of(new Cell(0, 16, 2), new Cell(0, 16, 3),
                            new Cell(1, 16, 3), new Cell(2, 16, 3), new Cell(2, 16, 2)),
                    Map.of(), Map.of(), null);
            registry.put(disconnected);
            assertProblem(Message.PLOT_ERROR_SUBPLOT_DISCONNECTED,
                    () -> registry.createSubPlot(disconnected.id(), owner, "Islands",
                            point(world, 0, 64, 8), point(world, 8, 64, 8)));
        }
    }

    @Test
    void hierarchyAndPresentationSurviveReloadAndParentCannotBeReleasedFirst()
            throws Exception {
        Path db = directory.resolve("persist.db");
        UUID parentId, childId, world = UUID.randomUUID(), owner = UUID.randomUUID();
        try (PlotRepository store = new PlotRepository(db)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot parent = parent(world, owner, Map.of());
            registry.put(parent);
            Plot child = registry.createSubPlot(parent.id(), UUID.randomUUID(), "North",
                    point(world, 0, 64, 0), point(world, 3, 64, 3));
            parentId = parent.id();
            childId = child.id();
            registry.setNoticeBoard(childId, "Welcome to North");
            registry.setAtmosphere(parentId,
                    new PlotAtmosphere(6000, PlotAtmosphere.Weather.CLEAR));
            registry.setAtmosphere(childId,
                    new PlotAtmosphere(null, PlotAtmosphere.Weather.RAIN));
            assertEquals(new PlotAtmosphere(6000, PlotAtmosphere.Weather.RAIN),
                    registry.effectiveAtmosphere(childId));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_CHILDREN,
                    () -> registry.remove(parent));
            assertThrows(SQLException.class, () -> store.delete(parentId));
        }
        try (PlotRepository store = new PlotRepository(db)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            Plot child = registry.byId(childId);
            assertEquals(parentId, child.parentId());
            assertEquals("Welcome to North", registry.noticeBoard(childId));
            assertEquals(new PlotAtmosphere(6000, PlotAtmosphere.Weather.RAIN),
                    registry.effectiveAtmosphere(childId));
            registry.remove(child);
            assertNull(registry.byId(childId));
            assertEquals(parentId, registry.at(world, 0, 64, 0).id());
            registry.remove(registry.byId(parentId));
            assertNull(registry.at(world, 0, 64, 0));
        }
    }

    @Test
    void parentOwnerCanAdjustAndReassignWithoutChangingChildIdentityOrSettings()
            throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("adjust.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            UUID world = UUID.randomUUID(), parentOwner = UUID.randomUUID();
            UUID childOwner = UUID.randomUUID(), successor = UUID.randomUUID();
            Plot parent = parent(world, parentOwner, Map.of());
            registry.put(parent);
            Plot child = registry.createSubPlot(parent.id(), childOwner, "East",
                    point(world, 0, 64, 0), point(world, 3, 64, 3));
            registry.setNoticeBoard(child.id(), "East gate");

            edits.flag(child, childOwner, false, "owner.manage_area", false);

            assertProblem(Message.PLOT_ERROR_SUBPLOT_OWNER,
                    () -> edits.resizeSubPlot(child, childOwner, false,
                            point(world, 0, 64, 0), point(world, 7, 64, 3)));
            edits.resizeSubPlot(child, parentOwner, false,
                    point(world, 0, 64, 0), point(world, 7, 64, 3));
            assertEquals(child.id(), registry.at(world, 6, 64, 0).id());
            assertEquals("East gate", registry.noticeBoard(child.id()));
            assertEquals(parent.id(), registry.byId(child.id()).parentId());

            registry.setArrival(child.id(), new PlotArrival(1, 64, 1, 0, 0, "", ""));
            assertProblem(Message.PLOT_ERROR_SUBPLOT_OWN_ARRIVAL,
                    () -> edits.resizeSubPlot(child, parentOwner, false,
                            point(world, 4, 64, 0), point(world, 7, 64, 3)));
            edits.transfer(child, parentOwner, successor);
            assertEquals(successor, registry.byId(child.id()).owner());
            assertFalse(registry.allowed(world, 0, 64, 0, childOwner,
                    false, false, "build"));
            assertTrue(registry.allowed(world, 0, 64, 0, successor,
                    false, false, "build"));
            assertTrue(registry.allowed(world, 0, 64, 0, parentOwner,
                    false, false, "build"));

            UUID newParentOwner = UUID.randomUUID();
            edits.transfer(parent, parentOwner, newParentOwner);
            assertFalse(registry.allowed(world, 0, 64, 0, parentOwner,
                    false, false, "build"));
            assertTrue(registry.allowed(world, 0, 64, 0, newParentOwner,
                    false, false, "build"));
            assertEquals(successor, registry.byId(child.id()).owner());
        }
    }

    private static Plot parent(UUID world, UUID owner, Map<UUID, Plot.Role> members) {
        return new Plot(UUID.randomUUID(), world, owner, "Town", true, 0L,
                Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(2, 16, 0)),
                members, Map.of("newcomer.door", true), null);
    }

    private static PlotPosition point(UUID world, int x, int y, int z) {
        return new PlotPosition(world, x, y, z);
    }

    private static void assertProblem(Message expected, ThrowingOperation action) {
        assertEquals(expected, assertThrows(PlotProblem.class, action::run).message());
    }

    @FunctionalInterface
    private interface ThrowingOperation { void run() throws Exception; }
}
