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

class PlotDeletionTest {
    @TempDir Path directory;

    @Test
    void ownerDeletionRemovesOnlyTheTargetAndItsPresentationAfterReload() throws Exception {
        Path database = directory.resolve("delete.db");
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Plot target = plot(world, owner, 0);
        Plot neighbor = plot(world, UUID.randomUUID(), 100);
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(target);
            registry.put(neighbor);
            registry.setNoticeBoard(target.id(), "Welcome");
            registry.setArrival(target.id(), new PlotArrival(0.5, 65, 0.5, 0, 0, "Workshop", ""));
            registry.setAtmosphere(target.id(), new PlotAtmosphere(6000, PlotAtmosphere.Weather.CLEAR));
            PlotEdits edits = new PlotEdits(registry);
            edits.delete(edits.releasable(owner, false, target.id().toString()), owner, false);
            assertRemoved(registry, target.id());
            assertNull(registry.at(world, 0, 64, 0));
            assertEquals(neighbor, registry.byId(neighbor.id()));
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            assertRemoved(registry, target.id());
            assertEquals(neighbor, registry.byId(neighbor.id()));
        }
    }

    @Test
    void collaboratorsAndPlotAdminsCannotReleaseButServerStaffCan() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("authority.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID();
            UUID admin = UUID.randomUUID();
            UUID collaborator = UUID.randomUUID();
            Plot target = plot(UUID.randomUUID(), owner, 0);
            registry.put(target);
            PlotEdits edits = new PlotEdits(registry);
            edits.invite(target, owner, false, admin, Plot.Role.ADMIN);
            edits.invite(registry.byId(target.id()), owner, false, collaborator, Plot.Role.COLLABORATOR);
            for (UUID actor : Set.of(admin, collaborator, UUID.randomUUID())) {
                assertEquals(Message.PLOT_ERROR_RELEASE_OWNER_ONLY, assertThrows(PlotProblem.class,
                        () -> edits.releasable(actor, false, target.id().toString())).message());
            }
            edits.delete(edits.releasable(admin, true, target.id().toString()), admin, true);
            assertRemoved(registry, target.id());
        }
    }

    @Test
    void ownershipIsRecheckedIfAConfirmationOutlivesATransfer() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("stale-confirmation.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID owner = UUID.randomUUID();
            UUID successor = UUID.randomUUID();
            Plot target = plot(UUID.randomUUID(), owner, 0);
            registry.put(target);
            PlotEdits edits = new PlotEdits(registry);
            edits.releasable(owner, false, target.id().toString());
            edits.transfer(target, owner, successor);
            assertEquals(Message.PLOT_ERROR_RELEASE_OWNER_ONLY, assertThrows(PlotProblem.class,
                    () -> edits.releasable(owner, false, target.id().toString())).message());
            assertEquals(successor, registry.byId(target.id()).owner());
            edits.delete(edits.releasable(successor, false, target.id().toString()), successor, false);
            assertRemoved(registry, target.id());
        }
    }

    @Test
    void newlyAddedSubplotsBlockParentDeletionAndParentOwnerCanReleaseChildren() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("children.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID world = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            Plot parent = plot(world, owner, 0);
            registry.put(parent);
            PlotEdits edits = new PlotEdits(registry);
            Plot confirmed = edits.releasable(owner, false, parent.id().toString());
            Plot child = registry.createSubPlot(parent.id(), UUID.randomUUID(), "Room",
                    new PlotPosition(world, 0, 64, 0), new PlotPosition(world, 3, 67, 3));
            assertEquals(Message.PLOT_ERROR_SUBPLOT_CHILDREN,
                    assertThrows(PlotProblem.class, () -> edits.delete(confirmed, confirmed.owner(), false)).message());
            assertTrue(registry.canRelease(child, owner, false));
            assertFalse(registry.childrenOf(parent.id()).isEmpty());
            edits.delete(edits.releasable(owner, false, child.id().toString()), owner, false);
            assertTrue(registry.childrenOf(parent.id()).isEmpty());
            assertEquals(parent.id(), registry.at(world, 0, 64, 0).id());
            edits.delete(edits.releasable(owner, false, parent.id().toString()), owner, false);
            assertRemoved(registry, parent.id());
        }
    }

    @Test
    void failedStorageDeletionDoesNotRemoveTheLivePlotOrSettings() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("failed-delete.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot target = plot(UUID.randomUUID(), UUID.randomUUID(), 0);
            registry.put(target);
            registry.setNoticeBoard(target.id(), "Keep this notice");
            long revision = registry.revision();
            store.close();
            PlotEdits edits = new PlotEdits(registry);
            assertThrows(SQLException.class, () -> edits.delete(target, target.owner(), false));
            assertEquals(target, registry.byId(target.id()));
            assertEquals("Keep this notice", registry.noticeBoard(target.id()));
            assertEquals(revision, registry.revision());
        }
    }

    private static void assertRemoved(PlotRegistry registry, UUID id) {
        assertNull(registry.byId(id));
        assertNull(registry.arrival(id));
        assertEquals("", registry.noticeBoard(id));
        assertTrue(registry.atmosphere(id).followsWorld());
    }

    private static Plot plot(UUID world, UUID owner, int blockX) {
        return new Plot(UUID.randomUUID(), world, owner, "Workshop", false, 0,
                Set.of(Cell.at(blockX, 64, 0)), Map.of(), Map.of(), null);
    }
}
