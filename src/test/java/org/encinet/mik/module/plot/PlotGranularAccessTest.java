package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotGranularAccessTest {
    @TempDir Path directory;

    @Test
    void catalogHasUniqueOperationsAndShortCategories() {
        assertEquals(27, PlotPermission.values().length);
        assertEquals(27, Arrays.stream(PlotPermission.values()).map(PlotPermission::key).distinct().count());
        for (PlotPermission.Category category : PlotPermission.Category.values()) {
            assertFalse(category.permissions().isEmpty());
            assertTrue(category.permissions().size() <= 7);
            for (PlotPermission action : category.permissions()) assertSame(category, action.category());
        }
        assertNull(PlotPermission.fromKey("unknown"));
        assertFalse(PlotAccessPolicy.validAction("build"));
    }

    @Test
    void classificationsPartitionTheCatalogAndKeepItemAccessSeparateFromBulkEditing() {
        Set<PlotPermission> categorized = new java.util.HashSet<>();
        for (PlotPermission.Category category : PlotPermission.Category.values()) {
            for (PlotPermission permission : category.permissions()) assertTrue(categorized.add(permission));
        }
        assertEquals(Set.of(PlotPermission.values()), categorized);
        assertEquals(Set.of(PlotPermission.CONTAINER, PlotPermission.ENTITY_STORAGE, PlotPermission.ITEM_PICKUP,
                PlotPermission.ITEM_DROP), Set.copyOf(PlotPermission.Category.STORAGE.permissions()));
        assertThrows(UnsupportedOperationException.class, () -> PlotPermission.Category.STORAGE.permissions().clear());
        Plot root = plot(null, Map.of("collaborator.item_pickup", false, "collaborator.item_drop", false),
                Map.of(UUID.randomUUID(), Plot.Role.COLLABORATOR));
        UUID member = root.members().keySet().iterator().next();
        assertTrue(root.allows("build", member, false, false));
        assertFalse(root.allows("item_pickup", member, false, false));
        assertFalse(root.allows("item_drop", member, false, false));
        for (PlotPermission permission : PlotPermission.Category.BUILD.permissions()) {
            Plot restricted = plot(null, Map.of("collaborator." + permission.key(), false),
                    Map.of(member, Plot.Role.COLLABORATOR));
            assertFalse(restricted.allows("build", member, false, false), permission.key());
        }
    }

    @Test
    void ownersAndAdminsFollowTheSameOverridesAsCollaborators() {
        UUID admin = UUID.randomUUID();
        UUID collaborator = UUID.randomUUID();
        Plot root = plot(null, Map.of("owner.place", false, "admin.break", false,
                "collaborator.container", false, PlotAccessPolicy.playerKey(admin, "door"), false),
                Map.of(admin, Plot.Role.ADMIN, collaborator, Plot.Role.COLLABORATOR));
        assertFalse(root.allows("place", root.owner(), false, false));
        assertFalse(root.allows("break", admin, false, false));
        assertFalse(root.allows("door", admin, false, false));
        assertFalse(root.allows("container", collaborator, false, false));
        assertTrue(root.allows("break", root.owner(), false, false));
        assertTrue(root.allows("place", admin, false, false));
        assertTrue(root.allows("place", UUID.randomUUID(), false, true));
    }

    @Test
    void childInheritsGroupRulesButNotParentMembershipOrIndividualGrants() {
        UUID collaborator = UUID.randomUUID();
        UUID parentMember = UUID.randomUUID();
        Plot root = plot(null, Map.of("collaborator.break", false,
                PlotAccessPolicy.playerKey(collaborator, "break"), true,
                PlotAccessPolicy.playerKey(parentMember, "place"), true),
                Map.of(collaborator, Plot.Role.COLLABORATOR, parentMember, Plot.Role.ADMIN));
        Plot child = plot(root, Map.of(), Map.of(collaborator, Plot.Role.COLLABORATOR));
        assertFalse(child.allows(root, "break", collaborator, false, false));
        assertFalse(child.allows(root, "place", parentMember, false, false));
        assertTrue(child.allows(root, "place", root.owner(), false, false));
    }

    @Test
    void personalSettingsWinOnlyForCurrentlyInvitedMembers() {
        UUID collaborator = UUID.randomUUID();
        Map<String, Boolean> flags = Map.of("collaborator.break", false,
                PlotAccessPolicy.playerKey(collaborator, "break"), true,
                PlotAccessPolicy.playerKey(collaborator, "place"), false);
        Plot root = plot(null, flags, Map.of(collaborator, Plot.Role.COLLABORATOR));
        assertTrue(root.allows("break", collaborator, false, false));
        assertFalse(root.allows("place", collaborator, false, false));
        Plot removed = plot(null, flags, Map.of());
        assertFalse(removed.allows("break", collaborator, false, false));
        assertEquals(PlotAccessPolicy.Source.LOCAL, PlotAccessPolicy.view(root, null,
                PlotAccessPolicy.Subject.player(collaborator), "break").source());
    }

    @Test
    void resettingOneCategoryLeavesOtherSubjectsAndCategoriesUntouched() {
        PlotAccessPolicy.Subject admin = PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.ADMIN);
        Map<String, Boolean> flags = Map.of("admin.container", false, "admin.ride", false,
                "admin.manage_members", false, "newcomer.door", true, "explosion", false);
        Map<String, Boolean> reset = PlotAccessPolicy.withoutSubject(flags, admin, PlotPermission.Category.STORAGE);
        assertEquals(Map.of("admin.ride", false, "admin.manage_members", false,
                "newcomer.door", true, "explosion", false), reset);
        assertTrue(PlotAccessPolicy.hasSubjectOverrides(flags, admin, PlotPermission.Category.STORAGE));
        assertFalse(PlotAccessPolicy.hasSubjectOverrides(flags, admin, PlotPermission.Category.BUILD));
    }

    @Test
    void resettingAllAccessPreservesUnknownAndEnvironmentFlags() {
        UUID player = UUID.randomUUID();
        Map<String, Boolean> flags = new HashMap<>();
        for (PlotPermission action : PlotPermission.values()) {
            flags.put("collaborator." + action.key(), false);
            flags.put(PlotAccessPolicy.playerKey(player, action.key()), true);
        }
        flags.put("fire", false);
        flags.put("unknown.action", true);
        flags.put("player.not-a-uuid.place", true);
        assertEquals(Map.of("fire", false, "unknown.action", true, "player.not-a-uuid.place", true),
                PlotAccessPolicy.withoutOverrides(flags));
    }

    @Test
    void groupAndPersonalPermissionsPersistAndRemovalClearsPersonalOverrides() throws Exception {
        Path database = directory.resolve("granular.db");
        UUID collaborator = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID owner;
        UUID plotId;
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            Plot root = plot(null, Map.of(), Map.of(collaborator, Plot.Role.COLLABORATOR, admin, Plot.Role.ADMIN));
            owner = root.owner();
            plotId = root.id();
            registry.put(root);
            PlotEdits edits = new PlotEdits(registry);
            PlotAccessPolicy.Subject subject = PlotAccessPolicy.Subject.player(admin);
            assertEquals(PlotAccessPolicy.Setting.ALLOW, edits.cycleAccess(root, owner, false, subject, "break"));
            assertEquals(PlotAccessPolicy.Setting.DENY, edits.cycleAccess(root, owner, false, subject, "break"));
            edits.flag(root, owner, false, "owner.place", false);
            assertFalse(registry.byId(plotId).allows("break", admin, false, false));
            assertFalse(registry.byId(plotId).allows("place", owner, false, false));
        }
        try (PlotRepository store = new PlotRepository(database)) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            Plot root = registry.byId(plotId);
            assertFalse(root.allows("break", admin, false, false));
            assertFalse(root.allows("place", owner, false, false));
            PlotEdits edits = new PlotEdits(registry);
            edits.removeMember(root, owner, false, admin);
            assertFalse(registry.byId(plotId).flags().containsKey(PlotAccessPolicy.playerKey(admin, "break")));
            edits.invite(root, owner, false, admin, Plot.Role.ADMIN);
            assertTrue(registry.byId(plotId).allows("break", admin, false, false));
        }
    }

    @Test
    void individualEditsRecheckManagementAndMembershipOnEveryCommit() throws Exception {
        try (PlotRepository store = new PlotRepository(directory.resolve("authority.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            UUID collaborator = UUID.randomUUID();
            UUID admin = UUID.randomUUID();
            Plot root = plot(null, Map.of(), Map.of(collaborator, Plot.Role.COLLABORATOR, admin, Plot.Role.ADMIN));
            registry.put(root);
            PlotEdits edits = new PlotEdits(registry);
            PlotAccessPolicy.Subject subject = PlotAccessPolicy.Subject.player(collaborator);
            assertEquals(Message.PLOT_ERROR_OWNER_ONLY, assertThrows(PlotProblem.class,
                    () -> edits.cycleAccess(root, collaborator, false, subject, "container")).message());
            edits.cycleAccess(root, root.owner(), false, PlotAccessPolicy.Subject.player(admin), "break");
            edits.removeMember(root, root.owner(), false, collaborator);
            assertEquals(Message.PLOT_ERROR_MEMBER_MISSING, assertThrows(PlotProblem.class,
                    () -> edits.cycleAccess(root, root.owner(), false, subject, "container")).message());
            edits.removeMember(root, root.owner(), false, admin);
            assertEquals(Message.PLOT_ERROR_OWNER_ONLY, assertThrows(PlotProblem.class,
                    () -> edits.resetAccess(root, admin, false,
                            PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.MEMBER), null)).message());
        }
    }

    private static Plot plot(Plot parent, Map<String, Boolean> flags, Map<UUID, Plot.Role> members) {
        return new Plot(UUID.randomUUID(), parent == null ? UUID.randomUUID() : parent.world(),
                UUID.randomUUID(), "Granular test", false, 0, Set.of(PlotGeometry.Cell.at(0, 64, 0)),
                members, flags, parent == null ? null : parent.id());
    }
}
