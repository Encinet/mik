package org.encinet.mik.feature.community.governance.delivery;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.encinet.mik.protocol.GovernanceGameGrant;
import org.encinet.mik.shared.role.PlayerRole;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** The live projection and its durable save are separate; the caller serializes saves per user. */
public final class LuckPermsGovernanceRoles {
    private final LuckPerms permissions;
    private final Consumer<UUID> changed;

    public LuckPermsGovernanceRoles(LuckPerms permissions, Consumer<UUID> changed) {
        this.permissions = permissions;
        this.changed = changed;
    }

    public User loaded(UUID player) { return permissions.getUserManager().getUser(player); }

    public CompletableFuture<User> load(UUID player) {
        User loaded = loaded(player);
        return loaded != null ? CompletableFuture.completedFuture(loaded) : permissions.getUserManager().loadUser(player);
    }

    public void project(User user, GovernanceGameGrant grant) {
        PlayerRole role = switch (grant.getPermission()) {
            case MEMBER -> PlayerRole.MEMBER;
            case MODERATOR -> PlayerRole.MODERATOR;
            default -> throw new IllegalArgumentException("Unsupported governance role");
        };
        var entry = node(role);
        boolean mutated;
        if (grant.getEnabled()) {
            mutated = requireMutation(user.data().add(entry), DataMutateResult.FAIL_ALREADY_HAS);
            var primary = user.getPrimaryGroup();
            if ((matches(primary, PlayerRole.DEFAULT) || (role == PlayerRole.MODERATOR && matches(primary, PlayerRole.MEMBER)))
                    && !has(user, PlayerRole.CUSTODIAN)
                    && (role == PlayerRole.MODERATOR || !has(user, PlayerRole.MODERATOR))) {
                mutated |= user.setPrimaryGroup(role.id()).wasSuccessful();
            }
        } else {
            mutated = requireMutation(user.data().remove(entry), DataMutateResult.FAIL_LACKS);
            if (matches(user.getPrimaryGroup(), role)) {
                var next = has(user, PlayerRole.CUSTODIAN) ? PlayerRole.CUSTODIAN
                        : has(user, PlayerRole.MODERATOR) ? PlayerRole.MODERATOR
                        : has(user, PlayerRole.MEMBER) ? PlayerRole.MEMBER : PlayerRole.DEFAULT;
                mutated |= user.setPrimaryGroup(next.id()).wasSuccessful();
            }
        }
        if (mutated) changed.accept(user.getUniqueId());
    }

    public CompletableFuture<Void> save(User user) {
        return permissions.getUserManager().saveUser(user);
    }

    private static boolean requireMutation(DataMutateResult result, DataMutateResult alreadyApplied) {
        if (result.wasSuccessful()) return true;
        if (result == alreadyApplied) return false;
        throw new IllegalStateException("LuckPerms rejected governance node mutation: " + result);
    }

    private static boolean matches(String group, PlayerRole role) { return group.equalsIgnoreCase(role.id()); }

    private static boolean has(User user, PlayerRole role) { return user.getNodes().contains(node(role)); }

    private static InheritanceNode node(PlayerRole role) { return InheritanceNode.builder(role.id()).build(); }
}
