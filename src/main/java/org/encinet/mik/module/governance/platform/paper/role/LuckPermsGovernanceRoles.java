package org.encinet.mik.module.governance.platform.paper.role;

import org.encinet.mik.module.role.PlayerRole;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Single Paper-side gateway for governance-owned LuckPerms role operations. */
public final class LuckPermsGovernanceRoles {
    private final LuckPerms luckPerms;

    public LuckPermsGovernanceRoles(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public CompletableFuture<Void> promoteMember(UUID playerId) {
        return luckPerms.getUserManager().modifyUser(playerId, user -> {
            user.data().add(InheritanceNode.builder(PlayerRole.MEMBER.id()).build());
            if (!hasModeratorRole(user) && !hasCustodianRole(user)) {
                user.setPrimaryGroup(PlayerRole.MEMBER.id());
            }
        }).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> appointModerator(UUID playerId) {
        return luckPerms.getUserManager().modifyUser(playerId, user -> {
            if (hasCustodianRole(user)) return;
            user.data().add(InheritanceNode.builder(PlayerRole.MODERATOR.id()).build());
            user.setPrimaryGroup(PlayerRole.MODERATOR.id());
        }).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> removeModerator(UUID playerId) {
        return luckPerms.getUserManager().modifyUser(playerId,
                LuckPermsGovernanceRoles::removeModeratorRole)
                .thenApply(ignored -> null);
    }

    public CompletableFuture<Boolean> isModerator(UUID playerId) {
        return luckPerms.getUserManager().loadUser(playerId)
                .thenApply(LuckPermsGovernanceRoles::isGovernedModerator);
    }

    public CompletableFuture<Boolean> isMember(UUID playerId) {
        return luckPerms.getUserManager().loadUser(playerId)
                .thenApply(LuckPermsGovernanceRoles::hasFormalMemberRole);
    }

    public CompletableFuture<List<UUID>> moderatorIds() {
        return luckPerms.getUserManager().getUniqueUsers().thenCompose(playerIds -> {
            List<CompletableFuture<User>> loads = playerIds.stream()
                    .map(luckPerms.getUserManager()::loadUser)
                    .toList();
            return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> loads.stream()
                            .map(CompletableFuture::join)
                            .filter(LuckPermsGovernanceRoles::isGovernedModerator)
                            .map(User::getUniqueId)
                            .toList());
        });
    }

    private static boolean hasModeratorRole(User user) {
        return user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(node -> node.getGroupName()
                        .equalsIgnoreCase(PlayerRole.MODERATOR.id()));
    }

    private static boolean hasCustodianRole(User user) {
        return user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(node -> node.getGroupName()
                        .equalsIgnoreCase(PlayerRole.CUSTODIAN.id()));
    }

    static boolean hasFormalMemberRole(User user) {
        return user.getNodes(NodeType.INHERITANCE).stream()
                .map(node -> node.getGroupName())
                .anyMatch(group -> group.equalsIgnoreCase(PlayerRole.MEMBER.id())
                        || group.equalsIgnoreCase(PlayerRole.MODERATOR.id())
                        || group.equalsIgnoreCase(PlayerRole.CUSTODIAN.id()));
    }

    static boolean isGovernedModerator(User user) {
        return hasModeratorRole(user) && !hasCustodianRole(user);
    }

    static void removeModeratorRole(User user) {
        if (hasCustodianRole(user)) return;
        user.getNodes(NodeType.INHERITANCE).stream()
                .filter(node -> node.getGroupName().equalsIgnoreCase(PlayerRole.MODERATOR.id()))
                .toList()
                .forEach(user.data()::remove);
        if (user.getPrimaryGroup().equalsIgnoreCase(PlayerRole.MODERATOR.id())) {
            user.setPrimaryGroup(PlayerRole.MEMBER.id());
        }
    }
}
