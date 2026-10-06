package org.encinet.mik.module.governance.platform.paper.membership;

import org.encinet.mik.module.role.PlayerRole;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.governance.membership.CurrentMemberLookup;
import org.encinet.mik.module.role.RolePermissions;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Maintains the LuckPerms-backed current-member snapshot used by eligibility checks. */
public final class MembershipRoleSnapshot implements CurrentMemberLookup {
    private final JavaPlugin plugin;
    private final Set<UUID> currentMembers = ConcurrentHashMap.newKeySet();

    private volatile boolean ready;
    private LuckPerms luckPerms;
    private EventSubscription<UserDataRecalculateEvent> subscription;
    private BukkitTask refreshTask;

    public MembershipRoleSnapshot(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
        subscription = luckPerms.getEventBus().subscribe(
                plugin, UserDataRecalculateEvent.class,
                event -> remember(event.getUser()));
        refresh();
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::refresh,
                20L * 60L * 60L, 20L * 60L * 60L);
    }

    public void stop() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
        if (subscription != null) subscription.close();
        subscription = null;
        currentMembers.clear();
        ready = false;
        luckPerms = null;
    }

    public boolean hasMemberRole(Player player) {
        return RolePermissions.isMember(player);
    }

    public void remember(Player player) {
        remember(player.getUniqueId(), hasMemberRole(player));
    }

    public void rememberPromoted(UUID playerId) {
        currentMembers.add(playerId);
    }

    @Override
    public boolean isCurrentlyMember(UUID playerId, String playerName) {
        return !ready || currentMembers.contains(playerId);
    }

    private void remember(User user) {
        remember(user.getUniqueId(), hasFormalMemberGroup(user));
    }

    private void remember(UUID playerId, boolean member) {
        if (member) {
            currentMembers.add(playerId);
        } else {
            currentMembers.remove(playerId);
        }
    }

    private void refresh() {
        LuckPerms active = luckPerms;
        if (active == null) return;
        active.getUserManager().getUniqueUsers().thenCompose(playerIds -> {
            var loads = playerIds.stream()
                    .map(active.getUserManager()::loadUser)
                    .toList();
            return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> loads.stream()
                            .map(CompletableFuture::join)
                            .filter(MembershipRoleSnapshot::hasFormalMemberGroup)
                            .map(User::getUniqueId)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }).whenComplete((members, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not refresh LuckPerms member snapshot", error);
                return;
            }
            currentMembers.clear();
            currentMembers.addAll(members);
            ready = true;
        });
    }

    private static boolean hasFormalMemberGroup(User user) {
        return user.getNodes(net.luckperms.api.node.NodeType.INHERITANCE).stream()
                .map(node -> node.getGroupName().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(group -> group.equals(PlayerRole.MEMBER.id())
                        || group.equals(PlayerRole.MODERATOR.id())
                        || group.equals(PlayerRole.CUSTODIAN.id()));
    }
}
