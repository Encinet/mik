package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotAccessPlanTest {
    @TempDir Path directory;

    @Test
    void draftPublishesMemberAndAllOverridesTogetherAndPersists() throws Exception {
        UUID guest = UUID.randomUUID();
        Plot expected;
        try (Fixture fixture = fixture(Map.of(), Map.of())) {
            PlotMemberDraft original = PlotMemberDraft.of(fixture.plot, guest, Plot.Role.COLLABORATOR);
            PlotMemberDraft draft = original.cycle(PlotPermission.PLACE).cycle(PlotPermission.PLACE)
                    .cycle(PlotPermission.MANAGE_MEMBERS);
            PlotAccessPlan plan = fixture.edits.planAccess(fixture.plot, draft.request());
            assertFalse(fixture.current().members().containsKey(guest));
            assertTrue(fixture.current().flags().isEmpty());
            assertTrue(original.overrides().isEmpty());
            assertFalse(plan.after().allows("place", guest, false, false));
            assertTrue(plan.after().allows("manage_members", guest, false, false));
            assertEquals(Set.of(guest), plan.affectedPlayers());
            fixture.edits.applyAccess(plan, fixture.plot.owner(), false);
            expected = fixture.current();
            assertEquals(plan.after(), expected);
            assertEquals(Plot.Role.COLLABORATOR, expected.members().get(guest));
            assertEquals(false, expected.flags().get(PlotAccessPolicy.playerKey(guest, "place")));
            assertEquals(true, expected.flags().get(PlotAccessPolicy.playerKey(guest, "manage_members")));
        }
        try (PlotRepository store = new PlotRepository(directory.resolve("access.db"))) {
            store.open();
            PlotRegistry registry = new PlotRegistry(store);
            registry.load();
            assertEquals(expected, registry.byId(expected.id()));
        }
    }

    @Test
    void draftRoleChangesAndCategoryResetsKeepUnrelatedSettings() throws Exception {
        UUID member = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(member, Plot.Role.COLLABORATOR),
                Map.of(PlotAccessPolicy.playerKey(member, "door"), false))) {
            PlotMemberDraft draft = PlotMemberDraft.of(fixture.plot, member, Plot.Role.ADMIN)
                    .cycle(PlotPermission.PLACE).cycle(PlotPermission.MANAGE_AREA);
            assertEquals(Map.of(PlotPermission.DOOR, false, PlotPermission.PLACE, true,
                    PlotPermission.MANAGE_AREA, true), draft.overrides());
            PlotMemberDraft reset = draft.reset(PlotPermission.Category.BUILD).role(Plot.Role.COLLABORATOR);
            assertEquals(Map.of(PlotPermission.DOOR, false, PlotPermission.MANAGE_AREA, true), reset.overrides());
            assertTrue(reset.reset(null).overrides().isEmpty());
            PlotMemberDraft cycle = reset.cycle(PlotPermission.DOOR);
            assertFalse(cycle.overrides().containsKey(PlotPermission.DOOR));
            assertEquals(false, fixture.current().flags().get(PlotAccessPolicy.playerKey(member, "door")));
        }
    }

    @Test
    void actualAbilitiesProtectCollaboratorsWithoutSpecialRoleNames() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.COLLABORATOR, target, Plot.Role.COLLABORATOR),
                Map.of(PlotAccessPolicy.playerKey(manager, "manage_members"), true,
                        PlotAccessPolicy.playerKey(target, "manage_settings"), true))) {
            assertEquals(Message.PLOT_ERROR_ADMIN_OWNER_ONLY, assertThrows(PlotProblem.class,
                    () -> fixture.edits.removeMember(fixture.plot, manager, false, target)).message());
            assertEquals(Message.PLOT_ERROR_ADMIN_OWNER_ONLY, assertThrows(PlotProblem.class,
                    () -> fixture.edits.invite(fixture.plot, manager, false, target, Plot.Role.COLLABORATOR,
                            Map.of())).message());
            fixture.edits.flag(fixture.plot, fixture.plot.owner(), false,
                    "collaborator.manage_permissions", true);
            fixture.edits.removeMember(fixture.plot, manager, false, target);
            assertFalse(fixture.current().members().containsKey(target));
            assertFalse(fixture.current().flags().containsKey(PlotAccessPolicy.playerKey(target, "manage_settings")));
        }
    }

    @Test
    void adminsWithoutManagementAbilitiesAreNotArtificiallyProtected() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Map<String, Boolean> flags = new HashMap<>();
        for (PlotPermission permission : PlotPermission.Category.MANAGEMENT.permissions())
            flags.put(PlotAccessPolicy.Group.ADMIN.key(permission.key()), false);
        flags.put(PlotAccessPolicy.playerKey(manager, "manage_members"), true);
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.COLLABORATOR, target, Plot.Role.ADMIN), flags)) {
            fixture.edits.invite(fixture.plot, manager, false, UUID.randomUUID(), Plot.Role.ADMIN);
            fixture.edits.removeMember(fixture.plot, manager, false, target);
            assertFalse(fixture.current().members().containsKey(target));
        }
    }

    @Test
    void equivalentCapabilitiesHaveIdenticalMembershipProtection() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID collaborator = UUID.randomUUID();
        Map<String, Boolean> flags = new HashMap<>();
        flags.put(PlotAccessPolicy.playerKey(manager, "manage_members"), true);
        for (PlotPermission permission : PlotPermission.Category.MANAGEMENT.permissions())
            flags.put(PlotAccessPolicy.playerKey(collaborator, permission.key()),
                    permission != PlotPermission.TRANSFER && permission != PlotPermission.DELETE);
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.COLLABORATOR, admin, Plot.Role.ADMIN,
                collaborator, Plot.Role.COLLABORATOR), flags)) {
            for (UUID target : List.of(admin, collaborator)) {
                assertEquals(Message.PLOT_ERROR_ADMIN_OWNER_ONLY, assertThrows(PlotProblem.class,
                        () -> fixture.edits.removeMember(fixture.plot, manager, false, target)).message());
            }
        }
    }

    @Test
    void permissionManagerMayRevokeAbilitiesTheyDoNotHold() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.COLLABORATOR, target, Plot.Role.COLLABORATOR),
                Map.of(PlotAccessPolicy.playerKey(manager, "manage_permissions"), true,
                        PlotAccessPolicy.playerKey(target, "delete"), true))) {
            assertFalse(fixture.current().allows("delete", manager, false, false));
            PlotAccessPlan revoke = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.player(target), PlotPermission.DELETE, false));
            assertEquals(Set.of(PlotPermission.DELETE), revoke.revoked());
            fixture.edits.applyAccess(revoke, manager, false);
            assertFalse(fixture.current().allows("delete", target, false, false));
            assertEquals(Message.PLOT_ERROR_PERMISSION_GRANT, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(fixture.edits.planAccess(fixture.plot,
                            new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.player(target),
                                    PlotPermission.DELETE, true)), manager, false)).message());
        }
    }

    @Test
    void explicitAllowCannotFreezeInheritedAuthorityBeyondActorCapabilities() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.ADMIN, target, Plot.Role.COLLABORATOR),
                Map.of("collaborator.delete", true))) {
            PlotAccessPlan freeze = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.player(target), PlotPermission.DELETE, true));
            assertTrue(freeze.effects().isEmpty());
            assertEquals(Message.PLOT_ERROR_PERMISSION_GRANT, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(freeze, manager, false)).message());
            assertFalse(fixture.current().flags().containsKey(PlotAccessPolicy.playerKey(target, "delete")));
        }
    }

    @Test
    void resetAndDefaultCannotRestoreUndelegatedAbilities() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.ADMIN, target, Plot.Role.COLLABORATOR),
                Map.of("collaborator.delete", true, PlotAccessPolicy.playerKey(target, "delete"), false))) {
            for (PlotAccessRequest request : List.of(
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.player(target), PlotPermission.DELETE, null),
                    new PlotAccessRequest.Reset(PlotAccessPolicy.Subject.player(target), PlotPermission.Category.MANAGEMENT))) {
                assertEquals(Message.PLOT_ERROR_PERMISSION_GRANT, assertThrows(PlotProblem.class,
                        () -> fixture.edits.applyAccess(fixture.edits.planAccess(fixture.plot, request), manager, false)).message());
            }
        }
    }

    @Test
    void atomicInviteUsesFinalAbilitiesRatherThanUnrestrictedTemplate() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.COLLABORATOR),
                Map.of(PlotAccessPolicy.playerKey(manager, "manage_members"), true))) {
            Map<PlotPermission, Boolean> overrides = new HashMap<>();
            for (PlotPermission permission : PlotPermission.Category.MANAGEMENT.permissions()) overrides.put(permission, false);
            fixture.edits.invite(fixture.plot, manager, false, guest, Plot.Role.ADMIN, overrides);
            assertEquals(Plot.Role.ADMIN, fixture.current().members().get(guest));
            assertFalse(fixture.current().canManage(guest, false));
        }
    }

    @Test
    void membershipAuthorizationRejectsFinalGrantsOutsideActorAbilities() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.ADMIN), Map.of("admin.manage_area", false))) {
            assertEquals(Message.PLOT_ERROR_PERMISSION_GRANT, assertThrows(PlotProblem.class,
                    () -> fixture.edits.invite(fixture.plot, manager, false, guest, Plot.Role.COLLABORATOR,
                            Map.of(PlotPermission.MANAGE_AREA, true))).message());
            assertFalse(fixture.current().members().containsKey(guest));
        }
    }

    @Test
    void ownerGroupIsProtectedButOwnerAndStaffCanRecover() throws Exception {
        UUID admin = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(admin, Plot.Role.ADMIN), Map.of("owner.manage_members", false))) {
            PlotAccessRequest request = new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.group(
                    PlotAccessPolicy.Group.OWNER), PlotPermission.PLACE, false);
            PlotAccessPlan plan = fixture.edits.planAccess(fixture.plot, request);
            assertEquals(Message.PLOT_ERROR_PERMISSION_OWNER_SCOPE, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(plan, admin, false)).message());
            fixture.edits.applyAccess(plan, admin, true);
            fixture.edits.resetAccess(fixture.plot, fixture.plot.owner(), false,
                    PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.OWNER), null);
            assertTrue(fixture.current().allows("manage_members", fixture.plot.owner(), false, false));
            assertEquals(Message.PLOT_ERROR_FLAG, assertThrows(PlotProblem.class,
                    () -> fixture.edits.flag(fixture.plot, fixture.plot.owner(), false,
                            "owner.manage_permissions", false)).message());
        }
    }

    @Test
    void fabricatedEffectsCannotBypassCanonicalCommitAuthorization() throws Exception {
        UUID manager = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.ADMIN), Map.of())) {
            PlotAccessPlan legitimate = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Member(UUID.randomUUID(), Plot.Role.COLLABORATOR,
                            Map.of(PlotPermission.DELETE, true)));
            PlotAccessPlan forged = new PlotAccessPlan(legitimate.before(), legitimate.after(), legitimate.parent(),
                    legitimate.children(), legitimate.request(), List.of());
            assertEquals(Message.PLOT_ERROR_PERMISSION_GRANT, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(forged, manager, false)).message());
            assertEquals(fixture.plot, fixture.current());
        }
    }

    @Test
    void modifiedCandidateCannotPublishUnrequestedState() throws Exception {
        try (Fixture fixture = fixture(Map.of(), Map.of())) {
            PlotAccessPlan legitimate = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.MEMBER),
                            PlotPermission.DOOR, true));
            Plot candidate = copy(legitimate.after(), "Forged name", legitimate.after().flags());
            PlotAccessPlan forged = new PlotAccessPlan(legitimate.before(), candidate, legitimate.parent(),
                    legitimate.children(), legitimate.request(), legitimate.effects());
            assertEquals(Message.PLOT_ERROR_PERMISSION_CHANGED, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(forged, fixture.plot.owner(), false)).message());
            assertEquals(fixture.plot, fixture.current());
        }
    }

    @Test
    void changedActorPermissionsInvalidateCapturedConfirmation() throws Exception {
        UUID manager = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(manager, Plot.Role.ADMIN), Map.of())) {
            PlotAccessPlan plan = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Member(UUID.randomUUID(), Plot.Role.COLLABORATOR, Map.of()));
            fixture.edits.flag(fixture.plot, fixture.plot.owner(), false, "admin.manage_members", false);
            assertEquals(Message.PLOT_ERROR_PERMISSION_CHANGED, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(plan, manager, false)).message());
            assertEquals(1, fixture.current().members().size());
        }
    }

    @Test
    void targetRemovalOrRoleChangeInvalidatesPersonalConfirmation() throws Exception {
        UUID target = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(target, Plot.Role.COLLABORATOR), Map.of())) {
            PlotAccessPlan plan = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.player(target), PlotPermission.DELETE, true));
            fixture.edits.invite(fixture.plot, fixture.plot.owner(), false, target, Plot.Role.ADMIN);
            assertFalse(plan.current(fixture.registry));
            fixture.edits.removeMember(fixture.plot, fixture.plot.owner(), false, target);
            assertEquals(Message.PLOT_ERROR_PERMISSION_CHANGED, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(plan, fixture.plot.owner(), false)).message());
        }
    }

    @Test
    void personalOverridesAndParentSupervisionAreIncludedInChildImpacts() throws Exception {
        UUID target = UUID.randomUUID();
        UUID overridden = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(), Map.of())) {
            Plot child = fixture.child(Map.of(target, Plot.Role.COLLABORATOR, overridden, Plot.Role.COLLABORATOR),
                    Map.of(PlotAccessPolicy.playerKey(overridden, "place"), true));
            PlotAccessPlan plan = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.COLLABORATOR),
                            PlotPermission.PLACE, false));
            assertEquals(Set.of(child.id()), plan.affectedChildren());
            assertEquals(Set.of(target), plan.affectedPlayers());
            assertEquals(Set.of(PlotPermission.PLACE), plan.revoked());
            assertTrue(plan.effects().stream().anyMatch(effect -> effect.plot().equals(child.id())
                    && effect.subject().player() != null && effect.subject().player().equals(target) && !effect.after()));
            assertFalse(plan.effects().stream().anyMatch(effect -> effect.subject().player() != null
                    && (effect.subject().player().equals(overridden) || effect.subject().player().equals(fixture.plot.owner()))));
        }
    }

    @Test
    void localChildRulesAvoidInheritedEffectiveChanges() throws Exception {
        try (Fixture fixture = fixture(Map.of(), Map.of())) {
            fixture.child(Map.of(), Map.of("collaborator.place", true));
            PlotAccessPlan plan = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.COLLABORATOR),
                            PlotPermission.PLACE, false));
            assertTrue(plan.affectedChildren().isEmpty());
        }
    }

    @Test
    void parentChangesInvalidateChildPlanAndParentOwnerHasRecoveryAuthority() throws Exception {
        try (Fixture fixture = fixture(Map.of(), Map.of())) {
            Plot child = fixture.child(Map.of(), Map.of());
            PlotAccessPlan plan = fixture.edits.planAccess(child,
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.OWNER),
                            PlotPermission.PLACE, false));
            fixture.edits.authorizeAccess(plan, fixture.plot.owner(), false);
            fixture.edits.rename(fixture.plot, fixture.plot.owner(), false, "Changed parent");
            assertEquals(Message.PLOT_ERROR_PERMISSION_CHANGED, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(plan, child.owner(), false)).message());
            fixture.edits.applyAccess(fixture.edits.planAccess(child, plan.request()), fixture.plot.owner(), false);
            assertTrue(fixture.registry.permitted(fixture.registry.byId(child.id()), fixture.plot.owner(), false,
                    PlotPermission.PLACE));
            assertFalse(fixture.registry.permitted(fixture.registry.byId(child.id()), child.owner(), false,
                    PlotPermission.PLACE));
        }
    }

    @Test
    void childChangesOrNewChildrenInvalidateGroupConfirmations() throws Exception {
        try (Fixture fixture = fixture(Map.of(), Map.of())) {
            PlotAccessRequest request = new PlotAccessRequest.Permission(
                    PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.COLLABORATOR), PlotPermission.MANAGE_AREA, true);
            PlotAccessPlan plan = fixture.edits.planAccess(fixture.plot, request);
            Plot child = fixture.child(Map.of(), Map.of());
            assertFalse(plan.current(fixture.registry));
            PlotAccessPlan withChild = fixture.edits.planAccess(fixture.plot, request);
            fixture.edits.rename(child, child.owner(), false, "Changed child");
            assertEquals(Message.PLOT_ERROR_PERMISSION_CHANGED, assertThrows(PlotProblem.class,
                    () -> fixture.edits.applyAccess(withChild, fixture.plot.owner(), false)).message());
        }
    }

    @Test
    void unrelatedPlotChangesDoNotInvalidatePersonalPlans() throws Exception {
        UUID target = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(target, Plot.Role.COLLABORATOR), Map.of())) {
            PlotAccessPlan plan = fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Permission(PlotAccessPolicy.Subject.player(target), PlotPermission.DOOR, false));
            Plot unrelated = new Plot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Elsewhere", false,
                    0L, fixture.plot.cells(), Map.of(), Map.of(), null);
            fixture.registry.put(unrelated);
            fixture.child(Map.of(), Map.of());
            assertTrue(plan.current(fixture.registry));
            fixture.edits.applyAccess(plan, fixture.plot.owner(), false);
            assertFalse(fixture.current().allows("door", target, false, false));
        }
    }

    @Test
    void viewTracesPersonalGroupAndParentDefaultOrigins() throws Exception {
        UUID target = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(), Map.of("collaborator.door", false))) {
            Plot child = fixture.child(Map.of(target, Plot.Role.COLLABORATOR), Map.of());
            PlotAccessPolicy.Subject subject = PlotAccessPolicy.Subject.player(target);
            PlotAccessPolicy.View inherited = PlotAccessPolicy.view(child, fixture.plot, subject, "door");
            assertEquals(PlotAccessPolicy.Setting.DEFAULT, inherited.setting());
            assertFalse(inherited.allowed());
            assertEquals(PlotAccessPolicy.Source.GROUP, inherited.source());
            assertEquals(new PlotAccessPolicy.Origin(fixture.plot.id(), PlotAccessPolicy.Group.COLLABORATOR,
                    PlotAccessPolicy.Source.LOCAL), inherited.origin());
            PlotAccessPolicy.View fallback = PlotAccessPolicy.view(child, fixture.plot, subject, "place");
            assertTrue(fallback.allowed());
            assertEquals(new PlotAccessPolicy.Origin(fixture.plot.id(), PlotAccessPolicy.Group.COLLABORATOR,
                    PlotAccessPolicy.Source.ROLE), fallback.origin());
            fixture.edits.applyAccess(fixture.edits.planAccess(child, new PlotAccessRequest.Permission(subject,
                    PlotPermission.DOOR, true)), child.owner(), false);
            PlotAccessPolicy.View personal = PlotAccessPolicy.view(fixture.registry.byId(child.id()), fixture.plot, subject, "door");
            assertEquals(PlotAccessPolicy.Setting.ALLOW, personal.setting());
            assertEquals(new PlotAccessPolicy.Origin(child.id(), null, PlotAccessPolicy.Source.LOCAL), personal.origin());
        }
    }

    @Test
    void sensitiveRequestsRequireConfirmationButGameplayChangesDoNot() throws Exception {
        UUID target = UUID.randomUUID();
        try (Fixture fixture = fixture(Map.of(target, Plot.Role.COLLABORATOR), Map.of())) {
            PlotAccessPolicy.Subject subject = PlotAccessPolicy.Subject.player(target);
            assertFalse(fixture.edits.planAccess(fixture.plot,
                    new PlotAccessRequest.Permission(subject, PlotPermission.DOOR, false)).requiresConfirmation());
            for (PlotAccessRequest request : List.of(
                    new PlotAccessRequest.Permission(subject, PlotPermission.MANAGE_SETTINGS, false),
                    new PlotAccessRequest.Reset(subject, null),
                    new PlotAccessRequest.Member(target, Plot.Role.ADMIN, Map.of()),
                    new PlotAccessRequest.Remove(target))) {
                assertTrue(fixture.edits.planAccess(fixture.plot, request).requiresConfirmation());
            }
        }
    }

    private Fixture fixture(Map<UUID, Plot.Role> members, Map<String, Boolean> flags) throws Exception {
        PlotRepository store = new PlotRepository(directory.resolve("access.db"));
        store.open();
        PlotRegistry registry = new PlotRegistry(store);
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Access plan", false,
                0L, Set.of(PlotGeometry.Cell.at(0, 64, 0)), members, flags, null);
        registry.put(plot);
        return new Fixture(store, registry, new PlotEdits(registry), plot);
    }

    private static Plot copy(Plot plot, String name, Map<String, Boolean> flags) {
        return new Plot(plot.id(), plot.world(), plot.owner(), name, plot.publicProject(), plot.createdAt(),
                plot.cells(), plot.members(), flags, plot.parentId());
    }

    private record Fixture(PlotRepository store, PlotRegistry registry, PlotEdits edits, Plot plot) implements AutoCloseable {
        Plot current() { return registry.byId(plot.id()); }

        Plot child(Map<UUID, Plot.Role> members, Map<String, Boolean> flags) throws Exception {
            Plot child = registry.createSubPlot(plot.id(), UUID.randomUUID(), "Child",
                    new PlotPosition(plot.world(), 0, 64, 0), new PlotPosition(plot.world(), 3, 67, 3));
            child = new Plot(child.id(), child.world(), child.owner(), child.name(), false, child.createdAt(),
                    child.cells(), members, flags, child.parentId());
            registry.put(child);
            return child;
        }

        @Override public void close() throws Exception { store.close(); }
    }
}
