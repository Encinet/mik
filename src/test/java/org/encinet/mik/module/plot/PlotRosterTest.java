package org.encinet.mik.module.plot;

import org.encinet.mik.module.menu.FloatingMenuPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotRosterTest {
    @TempDir Path directory;

    @Test
    void ownerIsTheOnlyEntryWhenNoPlayersHaveBeenInvited() {
        Plot plot = plot(UUID.randomUUID(), Map.of(), Map.of(), null);
        assertEquals(1, PlotRoster.size(plot));
        assertEquals(1, PlotRoster.entries(plot).size());
        PlotRoster.Entry owner = PlotRoster.entries(plot).getFirst();
        assertEquals(plot.owner(), owner.id());
        assertTrue(owner.owner());
        assertEquals(PlotAccessPolicy.Group.OWNER, owner.group());
        assertEquals(PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.OWNER), owner.permissionSubject());
        assertTrue(plot.members().isEmpty());
    }

    @Test
    void invitedPlayersKeepTheirRoleAndPersonalPermissionSubject() {
        UUID admin = UUID.randomUUID();
        UUID collaborator = UUID.randomUUID();
        Plot plot = plot(UUID.randomUUID(), Map.of(admin, Plot.Role.ADMIN,
                collaborator, Plot.Role.COLLABORATOR), Map.of(), null);
        assertEquals(3, PlotRoster.size(plot));
        assertEquals(3, PlotRoster.entries(plot).size());
        assertEquals(PlotAccessPolicy.Group.ADMIN, PlotRoster.entry(plot, admin).group());
        assertEquals(PlotAccessPolicy.Group.COLLABORATOR, PlotRoster.entry(plot, collaborator).group());
        for (UUID player : plot.members().keySet()) {
            assertFalse(PlotRoster.entry(plot, player).owner());
            assertEquals(PlotAccessPolicy.Subject.player(player), PlotRoster.entry(plot, player).permissionSubject());
        }
        assertNull(PlotRoster.entry(plot, UUID.randomUUID()));
        assertThrows(UnsupportedOperationException.class, () -> PlotRoster.entries(plot).clear());
    }

    @Test
    void ownerIdentityWinsAndIsNotCountedTwice() {
        UUID owner = UUID.randomUUID();
        Plot plot = plot(owner, Map.of(owner, Plot.Role.COLLABORATOR), Map.of(), null);
        assertEquals(1, PlotRoster.size(plot));
        assertEquals(1, PlotRoster.entries(plot).size());
        assertTrue(PlotRoster.entry(plot, owner).owner());
        assertEquals(PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.OWNER),
                PlotRoster.entry(plot, owner).permissionSubject());
    }

    @Test
    void ownerCountsTowardPaginationAndShrinkingListsClampOldPages() {
        Map<UUID, Plot.Role> members = new HashMap<>();
        for (int index = 0; index < 6; index++) members.put(UUID.randomUUID(), Plot.Role.COLLABORATOR);
        Plot plot = plot(UUID.randomUUID(), members, Map.of(), null);
        FloatingMenuPage first = new FloatingMenuPage(0, PlotRoster.size(plot), 6);
        assertEquals(2, first.count());
        assertEquals(plot.owner(), first.slice(PlotRoster.entries(plot)).getFirst().id());
        assertEquals(1, first.next().slice(PlotRoster.entries(plot)).size());
        Plot empty = plot(plot.owner(), Map.of(), Map.of(), null);
        FloatingMenuPage clamped = new FloatingMenuPage(first.next().index(), PlotRoster.size(empty), 6);
        assertEquals(0, clamped.index());
        assertEquals(1, clamped.slice(PlotRoster.entries(empty)).size());
    }

    @Test
    void rosterDoesNotGrantOrOverridePermissions() {
        UUID owner = UUID.randomUUID();
        Plot plot = plot(owner, Map.of(), Map.of("owner.place", false,
                PlotAccessPolicy.playerKey(owner, "place"), true), null);
        PlotRoster.Entry entry = PlotRoster.entry(plot, owner);
        assertFalse(plot.allows("place", owner, false, false));
        assertFalse(PlotAccessPolicy.view(plot, null, entry.permissionSubject(), "place").allowed());
        assertTrue(PlotAccessPolicy.view(plot, null, entry.permissionSubject(), "manage_permissions").allowed());
        assertThrows(PlotProblem.class, () -> PlotAccessPlan.prepare(plot, null, java.util.List.of(),
                new PlotAccessRequest.Member(owner, Plot.Role.ADMIN, Map.of())));
        assertThrows(PlotProblem.class, () -> PlotAccessPlan.prepare(plot, null, java.util.List.of(),
                new PlotAccessRequest.Remove(owner)));
    }

    @Test
    void parentOwnerHasOversightWithoutBeingAddedToChildRoster() {
        Plot parent = plot(UUID.randomUUID(), Map.of(), Map.of(), null);
        Plot child = plot(UUID.randomUUID(), Map.of(), Map.of(), parent.id());
        assertEquals(1, PlotRoster.size(child));
        assertNull(PlotRoster.entry(child, parent.owner()));
        assertTrue(child.allows(parent, "place", parent.owner(), false, false));
        Plot sameOwner = plot(parent.owner(), Map.of(), Map.of(), parent.id());
        assertEquals(1, PlotRoster.size(sameOwner));
        assertTrue(PlotRoster.entry(sameOwner, parent.owner()).owner());
    }

    @Test
    void transferRebuildsRosterWithoutDuplicatingSuccessorOrRetainingFormerOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID successor = UUID.randomUUID();
        UUID collaborator = UUID.randomUUID();
        Plot before = plot(owner, Map.of(successor, Plot.Role.ADMIN,
                collaborator, Plot.Role.COLLABORATOR), Map.of(), null);
        try (PlotRepository store = new PlotRepository(directory.resolve("roster.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.put(before);
            new PlotEdits(registry).transfer(before, owner, false, successor);
            Plot after = registry.byId(before.id());
            assertEquals(2, PlotRoster.size(after));
            assertEquals(2, PlotRoster.entries(after).size());
            assertTrue(PlotRoster.entry(after, successor).owner());
            assertFalse(after.members().containsKey(successor));
            assertNull(PlotRoster.entry(after, owner));
            assertEquals(PlotAccessPolicy.Group.COLLABORATOR, PlotRoster.entry(after, collaborator).group());
            assertTrue(PlotRoster.entry(before, owner).owner());
            assertEquals(3, PlotRoster.size(before));
        }
    }

    private static Plot plot(UUID owner, Map<UUID, Plot.Role> members, Map<String, Boolean> flags, UUID parent) {
        return new Plot(UUID.randomUUID(), UUID.randomUUID(), owner, "Home", false, 0,
                Set.of(PlotGeometry.Cell.at(0, 64, 0)), members, flags, parent);
    }
}
