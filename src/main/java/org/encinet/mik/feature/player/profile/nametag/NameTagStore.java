package org.encinet.mik.feature.player.profile.nametag;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.ChatMetaNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Owns only permanent, positive, unscoped user decorations at the feature's priority. */
final class NameTagStore implements AutoCloseable {
    private static final int PRIORITY = 10_000;
    private final LuckPerms luckPerms;
    private final Set<UUID> saving = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    record Snapshot(Set<Node> nodes) {
        Snapshot { nodes = Set.copyOf(nodes); }
        Set<Node> version(NameTagField field) { return owned(nodes, field); }
    }

    record Change(Set<Node> expected, String value) {
        Change { expected = Set.copyOf(expected); Objects.requireNonNull(value); }
    }

    static final class Conflict extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        Conflict() { super("Custom name decoration changed since editing began"); }
    }

    NameTagStore(LuckPerms luckPerms) { this.luckPerms = Objects.requireNonNull(luckPerms); }

    Optional<Snapshot> read(UUID playerId) {
        if (closed) return Optional.empty();
        var user = luckPerms.getUserManager().getUser(playerId);
        if (user == null) return Optional.empty();
        return Optional.of(snapshot(user.data().toCollection()));
    }

    Optional<NameTagStyle> style(UUID playerId, String name) {
        if (closed) return Optional.empty();
        var user = luckPerms.getUserManager().getUser(playerId);
        if (user == null) return Optional.empty();
        return NameTagStyle.configured(user.data().toCollection()).map(tag -> new NameTagStyle(playerId, name, tag));
    }

    CompletableFuture<Void> save(UUID playerId, Map<NameTagField, Change> changes) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Name tags are disabled"));
        Map<NameTagField, Change> frozen = Map.copyOf(changes);
        if (frozen.isEmpty() || frozen.values().stream().anyMatch(change -> NameTagFormat.validate(change.value()).isPresent()))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid name decoration"));
        if (!saving.add(playerId)) return CompletableFuture.failedFuture(new IllegalStateException("A save is already pending"));
        try {
            var replacements = new EnumMap<NameTagField, Node>(NameTagField.class);
            frozen.forEach((field, change) -> {
                if (!change.value().isEmpty()) replacements.put(field, switch (field) {
                    case PREFIX -> PrefixNode.builder(change.value(), PRIORITY).build();
                    case SUFFIX -> SuffixNode.builder(change.value(), PRIORITY).build();
                });
            });
            return luckPerms.getUserManager().modifyUser(playerId, user -> {
                if (closed) throw new IllegalStateException("Name tags are disabled");
                // Serializes this feature's changes; other plugins retain their own concurrency contract.
                synchronized (user) { replace(user.data(), frozen, replacements); }
            }).whenComplete((_, _) -> saving.remove(playerId));
        } catch (RuntimeException error) {
            saving.remove(playerId);
            return CompletableFuture.failedFuture(error);
        }
    }

    static Snapshot snapshot(Collection<Node> nodes) {
        return new Snapshot(nodes.stream().filter(node -> owns(node, NameTagField.PREFIX) || owns(node, NameTagField.SUFFIX))
                .collect(Collectors.toSet()));
    }

    static boolean owns(Node node, NameTagField field) {
        boolean type = field == NameTagField.PREFIX ? node instanceof PrefixNode : node instanceof SuffixNode;
        return type && ((ChatMetaNode<?, ?>) node).getPriority() == PRIORITY && node.getValue()
                && !node.hasExpiry() && node.getContexts().isEmpty();
    }

    private static Set<Node> owned(Collection<Node> nodes, NameTagField field) {
        return nodes.stream().filter(node -> owns(node, field)).collect(Collectors.toUnmodifiableSet());
    }

    static void replace(NodeMap data, Map<NameTagField, Change> changes, Map<NameTagField, Node> replacements) {
        // Check every requested version before changing any nodes.
        Collection<Node> current = data.toCollection();
        changes.forEach((field, change) -> {
            if (!owned(current, field).equals(change.expected())) throw new Conflict();
        });
        data.clear(node -> changes.keySet().stream().anyMatch(field -> owns(node, field)));
        replacements.values().forEach(data::add);
    }

    @Override public void close() { closed = true; saving.clear(); }
}
