package org.encinet.mik.feature.community.governance.platform.paper.role;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import org.encinet.mik.feature.community.governance.roster.*;
import org.encinet.mik.shared.role.PlayerRole;

/** Searches group assignments in storage, then resolves only possible staff users. */
public final class LuckPermsStaffDirectory implements StaffDirectory, AutoCloseable {
    private final LuckPerms permissions;
    private final Clock clock;
    private final EventSubscription<NodeMutateEvent> changes;
    private CompletableFuture<List<StaffMember>> pending;
    private List<StaffMember> cached;
    private long expiresAt, revision;
    private boolean closed;

    public LuckPermsStaffDirectory(LuckPerms permissions, Object owner) {
        this(permissions, owner, Clock.systemUTC());
    }

    LuckPermsStaffDirectory(LuckPerms permissions, Object owner, Clock clock) {
        this.permissions = Objects.requireNonNull(permissions);
        this.clock = Objects.requireNonNull(clock);
        changes = permissions.getEventBus().subscribe(owner, NodeMutateEvent.class, _ -> invalidate());
    }

    public synchronized void invalidate() { revision++; cached = null; }

    @Override public synchronized CompletableFuture<List<StaffMember>> members() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Staff directory is closed"));
        if (cached != null && clock.millis() < expiresAt) return CompletableFuture.completedFuture(cached);
        if (pending != null) return pending.copy();
        long requestedRevision = revision;
        // Assign before attaching completion: a provider is allowed to complete synchronously.
        var result = new CompletableFuture<List<StaffMember>>();
        pending = result;
        CompletableFuture.completedFuture(null).thenCompose(_ -> load()).whenComplete((members, error) -> {
            synchronized (this) {
                pending = null;
                if (!closed && error == null && revision == requestedRevision) {
                    cached = List.copyOf(members);
                    // Temporary permissions can expire without a node mutation event.
                    expiresAt = clock.millis() + 5_000;
                }
            }
            if (error == null) result.complete(members); else result.completeExceptionally(error);
        });
        return result.copy();
    }

    private CompletableFuture<List<StaffMember>> load() {
        return permissions.getGroupManager().loadAllGroups().thenCompose(_ -> {
            var possibleGroups = new HashSet<String>();
            for (var group : permissions.getGroupManager().getLoadedGroups())
                if (staffRole(group, group.getName()) != null) possibleGroups.add(group.getName().toLowerCase(Locale.ROOT));
            return permissions.getUserManager().searchAll(permissions.getNodeMatcherFactory().keyStartsWith("group."))
                    .thenCompose(assignments -> {
                        var candidates = new HashSet<UUID>();
                        assignments.forEach((id, nodes) -> {
                            if (nodes.stream().anyMatch(node -> NodeType.INHERITANCE.matches(node) && node.getValue()
                                    && !node.hasExpired() && possibleGroups.contains(NodeType.INHERITANCE.cast(node).getGroupName().toLowerCase(Locale.ROOT))))
                                candidates.add(id);
                        });
                        // Include unsaved live changes as well as persisted offline assignments.
                        for (User user : permissions.getUserManager().getLoadedUsers())
                            if (staffRole(user, null) != null) candidates.add(user.getUniqueId());
                        var loads = candidates.stream().map(permissions.getUserManager()::loadUser).toList();
                        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApply(_ -> loads.stream()
                                .map(CompletableFuture::join).map(user -> {
                                    PlayerRole role = staffRole(user, null);
                                    return role == null ? null : new StaffMember(user.getUniqueId(), user.getUsername(), role,
                                            role == PlayerRole.MODERATOR && removable(user, possibleGroups));
                                }).filter(Objects::nonNull)
                                .sorted(Comparator.comparing(StaffMember::role).thenComparing(StaffMember::name, String.CASE_INSENSITIVE_ORDER)
                                        .thenComparing(StaffMember::id)).toList());
                    });
        });
    }

    /** Static server contexts give offline and online entries the same community-wide meaning. */
    private PlayerRole staffRole(PermissionHolder holder, String ownGroup) {
        var groups = new HashSet<String>();
        if (ownGroup != null) groups.add(ownGroup.toLowerCase(Locale.ROOT));
        holder.getInheritedGroups(permissions.getContextManager().getStaticQueryOptions())
                .forEach(group -> groups.add(group.getName().toLowerCase(Locale.ROOT)));
        if (groups.contains(PlayerRole.CUSTODIAN.id())) return PlayerRole.CUSTODIAN;
        return groups.contains(PlayerRole.MODERATOR.id()) ? PlayerRole.MODERATOR : null;
    }

    /** Governance removes its direct moderator grant; it cannot revoke another group's staff powers. */
    private boolean removable(User user, Set<String> staffGroups) {
        var effective = user.getInheritedGroups(permissions.getContextManager().getStaticQueryOptions()).stream()
                .map(group -> group.getName().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        var direct = user.getNodes(NodeType.INHERITANCE).stream().filter(node -> node.getValue() && !node.hasExpired())
                .map(node -> node.getGroupName().toLowerCase(Locale.ROOT)).filter(effective::contains).toList();
        return direct.contains(PlayerRole.MODERATOR.id()) && direct.stream()
                .noneMatch(group -> !group.equals(PlayerRole.MODERATOR.id()) && staffGroups.contains(group));
    }

    @Override public synchronized void close() {
        closed = true;
        changes.close();
        cached = null;
        revision++;
    }
}
