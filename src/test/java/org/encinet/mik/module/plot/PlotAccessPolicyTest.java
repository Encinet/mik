package org.encinet.mik.module.plot;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotAccessPolicyTest {
    @Test
    void groupsProvideDefaultsWithoutBypassingPermissionOverrides() {
        Plot root = plot(null, Map.of(), Map.of());
        for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values()) {
            for (PlotPermission permission : PlotPermission.values()) {
                boolean management = permission.category() == PlotPermission.Category.MANAGEMENT;
                boolean expected = group == PlotAccessPolicy.Group.OWNER
                        || group == PlotAccessPolicy.Group.ADMIN && permission != PlotPermission.TRANSFER
                        && permission != PlotPermission.DELETE
                        || group == PlotAccessPolicy.Group.COLLABORATOR && !management;
                assertEquals(expected, PlotAccessPolicy.view(root, null, group, permission.key()).allowed(),
                        group + "." + permission.key());
                Plot child = plot(root, Map.of(), Map.of());
                assertEquals(expected && permission != PlotPermission.CREATE_SUBPLOT,
                        PlotAccessPolicy.view(child, root, group, permission.key()).allowed());
            }
        }
    }

    @Test
    void threeStateCyclesPreserveDefaultRatherThanUnboxingNull() {
        assertNull(PlotAccessPolicy.Setting.DEFAULT.value());
        assertEquals(Boolean.TRUE, PlotAccessPolicy.Setting.ALLOW.value());
        assertEquals(Boolean.FALSE, PlotAccessPolicy.Setting.DENY.value());
        assertEquals(PlotAccessPolicy.Setting.ALLOW, PlotAccessPolicy.Setting.DEFAULT.next());
        assertEquals(PlotAccessPolicy.Setting.DENY, PlotAccessPolicy.Setting.ALLOW.next());
        assertEquals(PlotAccessPolicy.Setting.DEFAULT, PlotAccessPolicy.Setting.DENY.next());
    }

    @Test
    void permissionViewsMatchEnforcementForEveryGroupOverrideAndParentSetting() {
        UUID admin = UUID.randomUUID();
        UUID collaborator = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        for (PlotPermission permission : PlotPermission.values()) {
            for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values()) {
                for (PlotAccessPolicy.Setting parentSetting : PlotAccessPolicy.Setting.values()) {
                    Plot parent = plot(null, flags(group, permission, parentSetting), Map.of());
                    for (PlotAccessPolicy.Setting setting : PlotAccessPolicy.Setting.values()) {
                        for (boolean subPlot : new boolean[]{false, true}) {
                            Plot effectiveParent = subPlot ? parent : null;
                            Plot current = plot(effectiveParent, flags(group, permission, setting),
                                    Map.of(admin, Plot.Role.ADMIN, collaborator, Plot.Role.COLLABORATOR));
                            UUID actor = switch (group) {
                                case OWNER -> current.owner();
                                case ADMIN -> admin;
                                case COLLABORATOR -> collaborator;
                                case NEWCOMER, MEMBER -> outsider;
                            };
                            PlotAccessPolicy.View access = PlotAccessPolicy.view(current, effectiveParent,
                                    group, permission.key());
                            boolean member = group == PlotAccessPolicy.Group.MEMBER;
                            assertEquals(current.allows(effectiveParent, permission.key(), actor, member, false),
                                    access.allowed(), group + "." + permission.key());
                        }
                    }
                }
            }
        }
    }

    @Test
    void serverIdentityGroupsCannotReceiveManagementAuthority() {
        for (PlotAccessPolicy.Group group : Set.of(PlotAccessPolicy.Group.NEWCOMER, PlotAccessPolicy.Group.MEMBER)) {
            for (PlotPermission permission : PlotPermission.Category.MANAGEMENT.permissions()) {
                Plot root = plot(null, Map.of(group.key(permission.key()), true), Map.of());
                assertFalse(PlotAccessPolicy.view(root, null, group, permission.key()).allowed());
                assertFalse(PlotAccessPolicy.editable(PlotAccessPolicy.Subject.group(group), permission.key()));
            }
        }
    }

    @Test
    void removedVisitorAndLegacyKeysCannotGrantAccess() {
        Map<String, Boolean> removed = Map.of("visitor.container", true, "member.interact", true,
                "newcomer.entity", true, "container", true);
        Plot root = plot(null, removed, Map.of());
        assertNull(PlotAccessPolicy.group("visitor"));
        assertFalse(PlotAccessPolicy.validAction("interact"));
        assertFalse(PlotAccessPolicy.validAction("entity"));
        assertFalse(PlotAccessPolicy.hasOverrides(removed));
        assertFalse(root.allows("door", UUID.randomUUID(), true, false));
        assertFalse(root.allows("entity_interact", UUID.randomUUID(), false, false));
        assertFalse(root.allows("container", UUID.randomUUID(), false, false));
    }

    @Test
    void parentRulesAreOverriddenLocallyAndOwnerRecoveryRemainsAvailable() {
        Plot parent = plot(null, Map.of("admin.container", false, "collaborator.break", false), Map.of());
        Plot child = plot(parent, Map.of("admin.container", true, "owner.manage_permissions", false), Map.of());
        assertTrue(PlotAccessPolicy.view(child, parent, PlotAccessPolicy.Group.ADMIN, "container").allowed());
        assertFalse(PlotAccessPolicy.view(child, parent, PlotAccessPolicy.Group.COLLABORATOR, "break").allowed());
        PlotAccessPolicy.View recovery = PlotAccessPolicy.view(child, parent,
                PlotAccessPolicy.Group.OWNER, "manage_permissions");
        assertTrue(recovery.allowed());
        assertEquals(PlotAccessPolicy.Source.OWNER, recovery.source());
        assertFalse(PlotAccessPolicy.editable(PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.OWNER),
                "manage_permissions"));
    }

    private static Map<String, Boolean> flags(PlotAccessPolicy.Group group, PlotPermission permission,
                                               PlotAccessPolicy.Setting setting) {
        return setting.value() == null ? Map.of() : Map.of(group.key(permission.key()), setting.value());
    }

    private static Plot plot(Plot parent, Map<String, Boolean> flags, Map<UUID, Plot.Role> members) {
        return new Plot(UUID.randomUUID(), parent == null ? UUID.randomUUID() : parent.world(),
                UUID.randomUUID(), "Access test", false, 0, Set.of(PlotGeometry.Cell.at(0, 64, 0)),
                members, flags, parent == null ? null : parent.id());
    }
}
