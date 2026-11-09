package org.encinet.mik.feature.player.profile.nametag;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.matcher.NodeMatcherFactory;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.encinet.mik.feature.player.profile.PlayerNameTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.encinet.mik.feature.player.profile.nametag.NameTagField.*;
import static org.encinet.mik.feature.player.profile.nametag.NameTagTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class NameTagDirectoryTest {
    @Test void offlinePlayersWithDirectStylesAreIncludedWithoutLoadingTheirUserOrBukkitData() throws Exception {
        var backend = new DirectoryBackend();
        UUID offline = UUID.randomUUID(), empty = UUID.randomUUID(), contextual = UUID.randomUUID();
        backend.persisted.put(offline, List.of(decoration(PREFIX, "<red>Low ", 100, false, false, true),
                decoration(PREFIX, "<blue>High ", 200, false, false, true), decoration(SUFFIX, " <green>Suffix")));
        backend.persisted.put(empty, List.of());
        backend.persisted.put(contextual, List.of(decoration(SUFFIX, "World only", 10000, true, false, true),
                decoration(PREFIX, "Expired", 10000, false, true, true), decoration(PREFIX, "Negated", 10000, false, false, false)));
        backend.names.put(offline, "OfflinePlayer");
        try (var directory = new NameTagDirectory(backend.api)) {
            List<NameTagStyle> styles = directory.load().get(5, TimeUnit.SECONDS);
            assertEquals(List.of(new NameTagStyle(offline, "OfflinePlayer", new PlayerNameTag("<blue>High ", " <green>Suffix"))), styles);
            assertEquals(List.of(offline), new ArrayList<>(backend.lookups));
            assertEquals(2, backend.searches);
        }
    }

    @Test void loadedUserChangesOverrideSearchResultsIncludingClearingAStyle() throws Exception {
        var backend = new DirectoryBackend();
        UUID cleared = UUID.randomUUID(), changed = UUID.randomUUID(), newlyConfigured = UUID.randomUUID();
        backend.persisted.put(cleared, List.of(decoration(PREFIX, "Stale")));
        backend.persisted.put(changed, List.of(decoration(SUFFIX, "Stale suffix")));
        backend.loaded.put(cleared, List.of());
        backend.loaded.put(changed, List.of(decoration(SUFFIX, "Current suffix")));
        backend.loaded.put(newlyConfigured, List.of(decoration(PREFIX, "New prefix")));
        backend.names.put(changed, "B");
        backend.names.put(newlyConfigured, "A");
        try (var directory = new NameTagDirectory(backend.api)) {
            var styles = directory.load().get(5, TimeUnit.SECONDS);
            assertEquals(List.of(newlyConfigured, changed), styles.stream().map(NameTagStyle::id).toList());
            assertEquals(new PlayerNameTag("", "Current suffix"), styles.getLast().tag());
            assertEquals(2, backend.lookups.size());
        }
    }

    @Test void usernameLookupsAreBoundedAndDisablingCancelsOutstandingRequests() throws Exception {
        var backend = new DirectoryBackend();
        backend.holdNames = true;
        for (int i = 0; i < 24; i++) backend.persisted.put(new UUID(0, i), List.of(decoration(PREFIX, "Style")));
        var directory = new NameTagDirectory(backend.api);
        CompletableFuture<List<NameTagStyle>> load = directory.load();
        var first = new ArrayList<UUID>();
        for (int i = 0; i < 8; i++) { UUID id = backend.lookups.poll(5, TimeUnit.SECONDS); assertNotNull(id); first.add(id); }
        assertTrue(backend.lookups.isEmpty(), "do not start one request for every known player at once");
        assertFalse(load.isDone());
        first.forEach(id -> backend.pendingNames.get(id).complete("Player_" + id));
        for (int i = 0; i < 8; i++) assertNotNull(backend.lookups.poll(5, TimeUnit.SECONDS));
        assertTrue(backend.lookups.isEmpty());
        directory.close();
        assertThrows(java.util.concurrent.ExecutionException.class, () -> load.get(5, TimeUnit.SECONDS));
        assertTrue(backend.pendingNames.values().stream().allMatch(CompletableFuture::isDone));
        assertThrows(CompletionException.class, () -> directory.load().join());
        assertEquals(2, backend.searches, "a closed directory must not issue more searches");
    }

    @Test void missingUsernameStillKeepsTheStyleAndLookupFailureIsRetryable() throws Exception {
        var backend = new DirectoryBackend();
        UUID id = UUID.randomUUID();
        backend.persisted.put(id, List.of(decoration(SUFFIX, "Suffix")));
        try (var directory = new NameTagDirectory(backend.api)) {
            assertEquals(id.toString(), directory.load().get(5, TimeUnit.SECONDS).getFirst().name());
            backend.failNames = true;
            assertThrows(java.util.concurrent.ExecutionException.class, () -> directory.load().get(5, TimeUnit.SECONDS));
            backend.failNames = false;
            backend.names.put(id, "Resolved");
            assertEquals("Resolved", directory.load().get(5, TimeUnit.SECONDS).getFirst().name());
        }
    }

    @Test void equalPriorityStylesAreStableAndAnEmptyHighestPriorityValueDoesNotExposeALowerOne() {
        var a = decoration(PREFIX, "A", 100, false, false, true);
        var b = decoration(PREFIX, "B", 100, false, false, true);
        assertEquals(NameTagStyle.configured(List.of(a, b)), NameTagStyle.configured(List.of(b, a)));
        var empty = decoration(PREFIX, "", 200, false, false, true);
        assertTrue(NameTagStyle.configured(List.of(a, b, empty)).isEmpty());
    }

    private static final class DirectoryBackend {
        final Map<UUID, List<Node>> persisted = new HashMap<>();
        final Map<UUID, List<Node>> loaded = new HashMap<>();
        final Map<UUID, String> names = new ConcurrentHashMap<>();
        final BlockingQueue<UUID> lookups = new LinkedBlockingQueue<>();
        final Map<UUID, CompletableFuture<String>> pendingNames = new ConcurrentHashMap<>();
        final LuckPerms api;
        volatile boolean holdNames;
        volatile boolean failNames;
        int searches;

        DirectoryBackend() {
            var matchers = proxy(NodeMatcherFactory.class, (method, args) -> {
                if (!method.equals("type")) throw new AssertionError("Unexpected matcher: " + method);
                Object type = args[0];
                return proxy(NodeMatcher.class, (name, values) -> {
                    if (name.equals("test")) return type == NodeType.PREFIX ? values[0] instanceof PrefixNode : values[0] instanceof SuffixNode;
                    throw new AssertionError(name);
                });
            });
            var users = proxy(UserManager.class, (method, args) -> switch (method) {
                case "searchAll" -> {
                    searches++;
                    var result = new HashMap<UUID, Collection<Node>>();
                    NodeMatcher<?> matcher = (NodeMatcher<?>) args[0];
                    persisted.forEach((id, nodes) -> {
                        var matching = nodes.stream().filter(matcher).toList();
                        if (!matching.isEmpty()) result.put(id, matching);
                    });
                    yield CompletableFuture.completedFuture(result);
                }
                case "getLoadedUsers" -> {
                    var result = new java.util.HashSet<User>();
                    loaded.forEach((id, nodes) -> result.add(proxy(User.class, (name, _) -> switch (name) {
                        case "getUniqueId" -> id;
                        case "data" -> nodes(new ArrayList<>(nodes));
                        case "hashCode" -> id.hashCode();
                        case "equals" -> false;
                        default -> throw new AssertionError(name);
                    })));
                    yield Set.copyOf(result);
                }
                case "lookupUsername" -> {
                    UUID id = (UUID) args[0];
                    CompletableFuture<String> future = holdNames ? new CompletableFuture<>()
                            : failNames ? CompletableFuture.failedFuture(new IllegalStateException("Lookup unavailable"))
                            : CompletableFuture.completedFuture(names.get(id));
                    pendingNames.put(id, future);
                    lookups.add(id);
                    yield future;
                }
                default -> throw new AssertionError("Directory must not load users: " + method);
            });
            api = proxy(LuckPerms.class, (method, _) -> switch (method) {
                case "getUserManager" -> users;
                case "getNodeMatcherFactory" -> matchers;
                default -> throw new AssertionError(method);
            });
        }
    }

    @FunctionalInterface private interface Call { Object invoke(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (_, method, args) -> call.invoke(method.getName(), args)));
    }
}
