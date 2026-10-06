package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotManagementPermissionsTest {
    @TempDir Path directory;

    @Test
    void delegatedMemberManagementDoesNotGrantOtherManagementAbilities() throws Exception {
        try (PlotRepository store = repository("delegation.db")) {
            PlotRegistry registry = new PlotRegistry(store);
            UUID collaborator = UUID.randomUUID();
            Plot plot = plot(Map.of(collaborator, Plot.Role.COLLABORATOR), Map.of());
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            edits.cycleAccess(plot, plot.owner(), false, PlotAccessPolicy.Subject.player(collaborator),
                    PlotPermission.MANAGE_MEMBERS.key());
            Plot current = registry.byId(plot.id());
            assertTrue(registry.canManage(current, collaborator, false));
            for (PlotPermission permission : PlotPermission.Category.MANAGEMENT.permissions())
                assertEquals(permission == PlotPermission.MANAGE_MEMBERS,
                        registry.permitted(current, collaborator, false, permission));
            UUID guest = UUID.randomUUID();
            edits.invite(plot, collaborator, false, guest, Plot.Role.COLLABORATOR);
            assertEquals(Plot.Role.COLLABORATOR, registry.byId(plot.id()).members().get(guest));
            assertEquals(Message.PLOT_ERROR_ADMIN_OWNER_ONLY, assertThrows(PlotProblem.class,
                    () -> edits.invite(plot, collaborator, false, guest, Plot.Role.ADMIN)).message());
            assertThrows(PlotProblem.class, () -> edits.rename(plot, collaborator, false, "Denied"));
            assertThrows(PlotProblem.class, () -> edits.flag(plot, collaborator, false, "member.door", true));
            assertThrows(PlotProblem.class, () -> edits.delete(plot, collaborator, false));
            assertEquals(plot.name(), registry.byId(plot.id()).name());
        }
    }

    @Test
    void everyMutationChecksLatestPermissionsInsteadOfCapturedRole() throws Exception {
        try (PlotRepository store = repository("stale.db")) {
            PlotRegistry registry = new PlotRegistry(store);
            UUID admin = UUID.randomUUID();
            Plot plot = plot(Map.of(admin, Plot.Role.ADMIN), Map.of());
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            edits.flag(plot, plot.owner(), false, "admin.manage_settings", false);
            assertThrows(PlotProblem.class, () -> edits.rename(plot, admin, false, "Denied"));
            assertThrows(PlotProblem.class, () -> edits.setPublic(plot, admin, false, true));
            assertThrows(PlotProblem.class, () -> edits.setNoticeBoard(plot, admin, false, "Denied"));
            assertThrows(PlotProblem.class, () -> edits.setTime(plot, admin, false, 6000));
            assertThrows(PlotProblem.class, () -> edits.setWeather(plot, admin, false, PlotAtmosphere.Weather.CLEAR));
            edits.flag(plot, plot.owner(), false, "admin.manage_permissions", false);
            assertThrows(PlotProblem.class, () -> edits.flag(plot, admin, false, "admin.manage_settings", true));
            assertThrows(PlotProblem.class, () -> edits.resetAccess(plot, admin, false,
                    PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.ADMIN), null));
            assertEquals(plot.name(), registry.byId(plot.id()).name());
            assertFalse(registry.byId(plot.id()).publicProject());
        }
    }

    @Test
    void adminsCannotSelfElevateThroughFlagsCyclesOrReset() throws Exception {
        try (PlotRepository store = repository("escalation.db")) {
            PlotRegistry registry = new PlotRegistry(store);
            UUID admin = UUID.randomUUID();
            Plot plot = plot(Map.of(admin, Plot.Role.ADMIN), Map.of("owner.transfer", false));
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            assertEquals(Message.PLOT_ERROR_PERMISSION_GRANT, assertThrows(PlotProblem.class,
                    () -> edits.flag(plot, admin, false, "admin.transfer", true)).message());
            assertEquals(Message.PLOT_ERROR_PERMISSION_GRANT, assertThrows(PlotProblem.class,
                    () -> edits.cycleAccess(plot, admin, false, PlotAccessPolicy.Subject.player(admin), "delete")).message());
            assertEquals(Message.PLOT_ERROR_PERMISSION_OWNER_SCOPE, assertThrows(PlotProblem.class,
                    () -> edits.resetAccess(plot, admin, false,
                            PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.OWNER),
                            PlotPermission.Category.MANAGEMENT)).message());
            assertFalse(registry.permitted(registry.byId(plot.id()), admin, false, PlotPermission.TRANSFER));
            assertFalse(registry.permitted(registry.byId(plot.id()), plot.owner(), false, PlotPermission.TRANSFER));
            edits.resetAccess(plot, plot.owner(), false,
                    PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.OWNER), PlotPermission.Category.MANAGEMENT);
            assertTrue(registry.permitted(registry.byId(plot.id()), plot.owner(), false, PlotPermission.TRANSFER));
        }
    }

    @Test
    void roleAssignmentCannotGrantManagementCapabilitiesTheActorLacks() throws Exception {
        try (PlotRepository store = repository("roles.db")) {
            PlotRegistry registry = new PlotRegistry(store);
            UUID delegate = UUID.randomUUID();
            UUID guest = UUID.randomUUID();
            Plot plot = plot(Map.of(delegate, Plot.Role.COLLABORATOR),
                    Map.of(PlotAccessPolicy.playerKey(delegate, "manage_members"), true,
                            PlotAccessPolicy.playerKey(delegate, "manage_permissions"), true));
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            assertEquals(Message.PLOT_ERROR_PERMISSION_GRANT, assertThrows(PlotProblem.class,
                    () -> edits.invite(plot, delegate, false, guest, Plot.Role.ADMIN)).message());
            assertFalse(registry.byId(plot.id()).members().containsKey(guest));
            edits.invite(plot, plot.owner(), false, guest, Plot.Role.ADMIN);
            assertEquals(Plot.Role.ADMIN, registry.byId(plot.id()).members().get(guest));
        }
    }

    @Test
    void ownerCanRestrictOperationsWithoutLosingThePermissionRecoveryEntry() throws Exception {
        try (PlotRepository store = repository("recovery.db")) {
            PlotRegistry registry = new PlotRegistry(store);
            Plot plot = plot(Map.of(), Map.of());
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            for (PlotPermission permission : PlotPermission.values()) {
                if (permission == PlotPermission.MANAGE_PERMISSIONS) continue;
                edits.flag(plot, plot.owner(), false, "owner." + permission.key(), false);
            }
            Plot current = registry.byId(plot.id());
            assertFalse(current.canBuild(plot.owner(), false));
            assertTrue(registry.canManage(current, plot.owner(), false));
            assertTrue(registry.permitted(current, plot.owner(), false, PlotPermission.MANAGE_PERMISSIONS));
            assertEquals(Message.PLOT_ERROR_FLAG, assertThrows(PlotProblem.class,
                    () -> edits.flag(plot, plot.owner(), false, "owner.manage_permissions", false)).message());
            edits.resetAccess(plot, plot.owner(), false,
                    PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.OWNER), null);
            assertTrue(registry.byId(plot.id()).canBuild(plot.owner(), false));
        }
    }

    @Test
    void transferAndDeleteAreExplicitlyDelegatableIndependentCapabilities() throws Exception {
        try (PlotRepository store = repository("ownership.db")) {
            PlotRegistry registry = new PlotRegistry(store);
            UUID delegate = UUID.randomUUID();
            UUID successor = UUID.randomUUID();
            Plot plot = plot(Map.of(delegate, Plot.Role.COLLABORATOR), Map.of());
            registry.put(plot);
            PlotEdits edits = new PlotEdits(registry);
            edits.cycleAccess(plot, plot.owner(), false, PlotAccessPolicy.Subject.player(delegate), "transfer");
            edits.transfer(plot, delegate, false, successor);
            Plot current = registry.byId(plot.id());
            assertEquals(successor, current.owner());
            assertFalse(current.members().containsKey(plot.owner()));
            assertThrows(PlotProblem.class, () -> edits.delete(current, delegate, false));
            edits.cycleAccess(current, successor, false, PlotAccessPolicy.Subject.player(delegate), "delete");
            edits.delete(current, delegate, false);
            assertEquals(null, registry.byId(plot.id()));
        }
    }

    private PlotRepository repository(String filename) throws Exception {
        PlotRepository store = new PlotRepository(directory.resolve(filename));
        store.open();
        return store;
    }

    private static Plot plot(Map<UUID, Plot.Role> members, Map<String, Boolean> flags) {
        return new Plot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Management test",
                false, 0L, Set.of(PlotGeometry.Cell.at(0, 64, 0)), members, flags, null);
    }
}
