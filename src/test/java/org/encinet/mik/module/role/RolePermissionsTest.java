package org.encinet.mik.module.role;

import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionsTest {
    @Test
    void custodianCanModerateWithoutLuckPermsGroupInheritance() {
        Permissible custodian = withPermissions(RolePermissions.CUSTODIAN);
        assertTrue(RolePermissions.isCustodian(custodian));
        assertTrue(RolePermissions.canModerate(custodian));
        assertTrue(RolePermissions.isMember(custodian));

        Permissible moderator = withPermissions(RolePermissions.MODERATOR);
        assertFalse(RolePermissions.isCustodian(moderator));
        assertTrue(RolePermissions.canModerate(moderator));
        assertTrue(RolePermissions.isMember(moderator));

        assertFalse(RolePermissions.canModerate(withPermissions("group.manager", "group.helper")));
    }

    private static Permissible withPermissions(String... nodes) {
        Set<String> granted = Set.of(nodes);
        return (Permissible) Proxy.newProxyInstance(
                Permissible.class.getClassLoader(), new Class<?>[] {Permissible.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission"))
                        return granted.contains(args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
