package org.encinet.mik.feature.player.profile.nametag;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import org.encinet.mik.feature.player.profile.PlayerNameTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Searches persisted user decorations without loading every offline LuckPerms or Bukkit player. */
final class NameTagDirectory implements AutoCloseable {
    private static final int LOOKUP_BATCH = 8;
    private final LuckPerms permissions;
    private final Set<CompletableFuture<?>> requests = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    NameTagDirectory(LuckPerms permissions) { this.permissions = permissions; }

    CompletableFuture<List<NameTagStyle>> load() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Name tag directory is closed"));
        var matchers = permissions.getNodeMatcherFactory();
        var prefixes = request(() -> permissions.getUserManager().searchAll(matchers.type(NodeType.PREFIX)));
        var suffixes = request(() -> permissions.getUserManager().searchAll(matchers.type(NodeType.SUFFIX)));
        return prefixes.thenCombineAsync(suffixes, (first, second) -> {
            if (closed) throw new IllegalStateException("Name tag directory is closed");
            var nodes = new HashMap<UUID, Collection<? extends Node>>();
            first.forEach((id, values) -> nodes.put(id, new ArrayList<>(values)));
            second.forEach((id, values) -> {
                var combined = new ArrayList<Node>(nodes.getOrDefault(id, List.of()));
                combined.addAll(values);
                nodes.put(id, combined);
            });
            // Unsaved loaded data is authoritative over a possibly older database search result.
            permissions.getUserManager().getLoadedUsers().forEach(user -> nodes.put(user.getUniqueId(), List.copyOf(user.data().toCollection())));
            var tags = new HashMap<UUID, PlayerNameTag>();
            nodes.forEach((id, values) -> NameTagStyle.configured(values).ifPresent(tag -> tags.put(id, tag)));
            return Map.copyOf(tags);
        }).thenCompose(tags -> names(new ArrayList<>(tags.keySet()), tags, 0, new ArrayList<>()))
                .thenApply(styles -> styles.stream().sorted(Comparator.comparing(NameTagStyle::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(NameTagStyle::id)).toList());
    }

    private CompletableFuture<List<NameTagStyle>> names(List<UUID> ids, Map<UUID, PlayerNameTag> tags,
                                                        int offset, List<NameTagStyle> result) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Name tag directory is closed"));
        if (offset >= ids.size()) return CompletableFuture.completedFuture(List.copyOf(result));
        int end = Math.min(offset + LOOKUP_BATCH, ids.size());
        var batch = new ArrayList<CompletableFuture<NameTagStyle>>();
        for (UUID id : ids.subList(offset, end)) batch.add(request(() -> permissions.getUserManager().lookupUsername(id))
                .thenApply(name -> new NameTagStyle(id, name, tags.get(id))));
        return CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).thenComposeAsync(_ -> {
            batch.forEach(future -> result.add(future.join()));
            return names(ids, tags, end, result);
        });
    }

    private synchronized <T> CompletableFuture<T> request(Supplier<CompletableFuture<T>> action) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Name tag directory is closed"));
        try {
            CompletableFuture<T> source = action.get();
            CompletableFuture<T> future = source.copy().orTimeout(10, TimeUnit.SECONDS);
            requests.add(future);
            future.whenComplete((_, error) -> {
                requests.remove(future);
                if (error != null) source.cancel(false);
            });
            return future;
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
    }

    @Override public synchronized void close() {
        closed = true;
        requests.forEach(future -> future.cancel(false));
        requests.clear();
    }
}
