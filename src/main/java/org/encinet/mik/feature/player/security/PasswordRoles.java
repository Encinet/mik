package org.encinet.mik.feature.player.security;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import org.encinet.mik.shared.role.PlayerRole;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Off-thread LuckPerms lookup; protect staff groups even when their group marker is not inherited. */
final class PasswordRoles {
    private final LuckPerms luckPerms;
    private final java.util.function.Predicate<UUID> operator;
    PasswordRoles(LuckPerms luckPerms, java.util.function.Predicate<UUID> operator) {
        this.luckPerms = luckPerms;
        this.operator = operator;
    }

    record Target(UUID id, String name) { }

    /** Main-thread role checks must protect OP even when group permissions explicitly deny staff roles. */
    static PlayerRole role(Player player) {
        return player.isOp() ? PlayerRole.CUSTODIAN : PlayerRole.resolve(player);
    }

    Target lookup(String name) {
        try {
            UUID id;
            try {
                id = UUID.fromString(name);
                String known = luckPerms.getUserManager().lookupUsername(id).get(5, TimeUnit.SECONDS);
                return known == null ? null : new Target(id, known);
            } catch (IllegalArgumentException ignored) {
                id = luckPerms.getUserManager().lookupUniqueId(name).get(5, TimeUnit.SECONDS);
                return id == null ? null : new Target(id, name);
            }
        } catch (Exception error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
    }

    PlayerRole role(UUID id) {
        if (operator.test(id)) return PlayerRole.CUSTODIAN;
        User user = luckPerms.getUserManager().getUser(id);
        if (user == null) {
            try { user = luckPerms.getUserManager().loadUser(id).get(5, TimeUnit.SECONDS); }
            catch (Exception error) { throw new PasswordException(PasswordException.Code.STORAGE, error); }
        }
        var options = luckPerms.getContextManager().getStaticQueryOptions();
        var groups = user.getInheritedGroups(options);
        var permissions = user.getCachedData().getPermissionData(options);
        for (PlayerRole role : PlayerRole.values()) {
            if (permissions.checkPermission(role.permission()).asBoolean()
                    || groups.stream().anyMatch(group -> group.getName().equalsIgnoreCase(role.id()))
                    || user.getNodes(NodeType.INHERITANCE).stream()
                    .anyMatch(node -> node.getGroupName().equalsIgnoreCase(role.id()))) return role;
        }
        return PlayerRole.DEFAULT;
    }
}
