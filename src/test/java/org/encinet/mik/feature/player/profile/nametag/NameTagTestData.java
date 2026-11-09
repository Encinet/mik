package org.encinet.mik.feature.player.profile.nametag;

import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.node.types.ChatMetaNode;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** In-memory LuckPerms ports; production snapshot, ownership and replacement algorithms remain real. */
final class NameTagTestData {
    private NameTagTestData() { }

    static String customValue(NameTagStore.Snapshot snapshot, NameTagField field) {
        return snapshot.version(field).stream().map(node -> ((ChatMetaNode<?, ?>) node).getMetaValue()).sorted().findFirst().orElse("");
    }

    static CompletableFuture<Void> save(NameTagStore store, UUID id, NameTagField field, Set<Node> expected, String value) {
        return store.save(id, Map.of(field, new NameTagStore.Change(expected, value)));
    }

    static void replace(NodeMap data, NameTagField field, Set<Node> expected, Node replacement) {
        NameTagStore.replace(data, Map.of(field, new NameTagStore.Change(expected, "")),
                replacement == null ? Map.of() : Map.of(field, replacement));
    }

    record Pending(Consumer<User> mutation, CompletableFuture<Void> saved) { }

    static final class Backend {
        final UUID playerId = UUID.randomUUID();
        final List<Node> values;
        final List<Pending> pending = new ArrayList<>();
        final User user;
        final LuckPerms api;

        Backend(List<Node> values) {
            this.values = new ArrayList<>(values);
            NodeMap data = nodes(this.values);
            user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class}, (_, method, _) -> {
                if (method.getName().equals("data")) return data;
                throw new UnsupportedOperationException(method.getName());
            });
            var users = (UserManager) Proxy.newProxyInstance(UserManager.class.getClassLoader(), new Class<?>[]{UserManager.class},
                    (_, method, args) -> {
                        if (!playerId.equals(args[0])) throw new AssertionError("Wrong player");
                        return switch (method.getName()) {
                            case "getUser" -> user;
                            case "modifyUser" -> {
                                var save = new CompletableFuture<Void>();
                                @SuppressWarnings("unchecked") // UserManager.modifyUser supplies Consumer<User> to this proxy.
                                var edit = (Consumer<User>) args[1];
                                pending.add(new Pending(edit, save));
                                yield save;
                            }
                            default -> throw new AssertionError("Unexpected user I/O: " + method.getName());
                        };
                    });
            var builders = (NodeBuilderRegistry) Proxy.newProxyInstance(NodeBuilderRegistry.class.getClassLoader(), new Class<?>[]{NodeBuilderRegistry.class},
                    (_, method, _) -> switch (method.getName()) {
                        case "forPrefix" -> builder(NameTagField.PREFIX);
                        case "forSuffix" -> builder(NameTagField.SUFFIX);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            api = (LuckPerms) Proxy.newProxyInstance(LuckPerms.class.getClassLoader(), new Class<?>[]{LuckPerms.class},
                    (_, method, _) -> switch (method.getName()) {
                        case "getUserManager" -> users;
                        case "getNodeBuilderRegistry" -> builders;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    /** Models LuckPerms' plugin-owned factory registration and restores the process-wide API after a test. */
    static AutoCloseable provider(LuckPerms api) throws ReflectiveOperationException {
        var field = LuckPermsProvider.class.getDeclaredField("instance");
        field.setAccessible(true);
        var previous = field.get(null);
        field.set(null, api);
        return () -> field.set(null, previous);
    }

    private static Object builder(NameTagField field) {
        final class Draft { String raw; int priority; }
        var draft = new Draft();
        Class<?> type = field == NameTagField.PREFIX ? PrefixNode.Builder.class : SuffixNode.Builder.class;
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "prefix", "suffix" -> { draft.raw = (String) args[0]; yield proxy; }
            case "priority" -> { draft.priority = (int) args[0]; yield proxy; }
            case "build" -> decoration(field, draft.raw, draft.priority, false, false, true);
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    static Node decoration(NameTagField field, String raw) { return decoration(field, raw, 10_000, false, false, true); }

    static Node decoration(NameTagField field, String raw, int priority, boolean scoped, boolean temporary, boolean positive) {
        var contexts = (ImmutableContextSet) Proxy.newProxyInstance(ImmutableContextSet.class.getClassLoader(),
                new Class<?>[]{ImmutableContextSet.class}, (_, method, _) -> {
                    if (method.getName().equals("isEmpty")) return !scoped;
                    throw new UnsupportedOperationException(method.getName());
                });
        return (Node) Proxy.newProxyInstance(Node.class.getClassLoader(),
                new Class<?>[]{field == NameTagField.PREFIX ? PrefixNode.class : SuffixNode.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPriority" -> priority;
                    case "getMetaValue", "getPrefix", "getSuffix" -> raw;
                    case "getValue" -> positive;
                    case "hasExpiry" -> temporary;
                    case "getContexts" -> contexts;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> field + ":" + raw + ":" + priority;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static NodeMap nodes(List<Node> values) {
        return (NodeMap) Proxy.newProxyInstance(NodeMap.class.getClassLoader(), new Class<?>[]{NodeMap.class},
                (_, method, args) -> switch (method.getName()) {
                    case "toCollection" -> List.copyOf(values);
                    case "clear" -> {
                        @SuppressWarnings("unchecked") // NodeMap.clear declares Predicate<Node> at the proxy boundary.
                        var predicate = (Predicate<Node>) args[0];
                        values.removeIf(predicate);
                        yield null;
                    }
                    case "add" -> { values.add((Node) args[0]); yield DataMutateResult.SUCCESS; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
