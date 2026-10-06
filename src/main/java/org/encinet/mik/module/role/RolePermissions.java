package org.encinet.mik.module.role;

import org.bukkit.permissions.Permissible;

/** Role checks independent of the LuckPerms inheritance between staff groups. */
public final class RolePermissions {
    public static final String MEMBER = PlayerRole.MEMBER.permission();
    public static final String MODERATOR = PlayerRole.MODERATOR.permission();
    public static final String CUSTODIAN = PlayerRole.CUSTODIAN.permission();

    private RolePermissions() { }

    public static boolean isCustodian(Permissible subject) {
        return subject.hasPermission(CUSTODIAN);
    }

    public static boolean canModerate(Permissible subject) {
        return subject.hasPermission(MODERATOR) || isCustodian(subject);
    }

    public static boolean isMember(Permissible subject) {
        return subject.hasPermission(MEMBER) || canModerate(subject);
    }
}
