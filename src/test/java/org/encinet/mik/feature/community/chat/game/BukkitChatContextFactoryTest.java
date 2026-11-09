package org.encinet.mik.feature.community.chat.game;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityBinding;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitChatContextFactoryTest {
    @Test
    void inventoryRefreshesAndRepeatedCapturesReuseIndexesWhileNamesUpdateImmediately() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ChatProcessingContext first = fixture.capture(true);
            assertSame(first.mentions().online(), fixture.capture(true).mentions().online());
            fixture.refresh.run();
            assertSame(first.mentions().online(), fixture.capture(true).mentions().online());

            fixture.alice.name = "Alex";
            ChatProcessingContext renamed = fixture.capture(true);
            assertNotSame(first.mentions().online(), renamed.mentions().online());
            assertNull(renamed.mentions().find("@Alice", 0));
            assertEquals(fixture.alice.id, renamed.mentions().find("@Alex", 0).target().id());
            assertEquals(fixture.alice.id, first.mentions().find("@Alice", 0).target().id());

            fixture.primary = false;
            ChatProcessingContext async = fixture.capture(true);
            assertSame(renamed.mentions().online(), async.mentions().online());
            assertEquals(fixture.alice.id, async.mentions().find("@ Alex", 0).target().id());
            assertSame(fixture.alice.player, fixture.factory.onlinePlayer(fixture.alice.id));
        }
    }

    @Test
    void bindingsAreReusedDeduplicatedAndInvalidatedWithoutLeakingIntoPrivateChat() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UUID offline = UUID.randomUUID();
            fixture.bindings = List.of(binding(offline, "Offline", "one"),
                    binding(offline, "Offline", "two"),
                    binding(UUID.randomUUID(), "I", "three"),
                    binding(UUID.randomUUID(), "ı", "four"),
                    binding(fixture.sender.id, fixture.sender.name, "self"));
            fixture.refresh.run();
            ChatProcessingContext first = fixture.capture(true);
            assertEquals(offline, first.mentions().find("@Offline", 0).target().id());
            assertNull(first.mentions().find("@I", 0));
            assertNull(first.mentions().find("@Sender", 0));
            assertNull(fixture.capture(false).mentions().find("@Offline", 0));

            fixture.bindings = new ArrayList<>(fixture.bindings);
            fixture.refresh.run();
            assertSame(first.mentions().bound(), fixture.capture(true).mentions().bound());
            fixture.bindings = List.of(binding(offline, "Renamed", "one"));
            fixture.refresh.run();
            ChatProcessingContext renamed = fixture.capture(true);
            assertNotSame(first.mentions().bound(), renamed.mentions().bound());
            assertNull(renamed.mentions().find("@Offline", 0));
            assertEquals(offline, renamed.mentions().find("@Renamed", 0).target().id());
            assertEquals(offline, first.mentions().find("@Offline", 0).target().id());

            fixture.bindings = List.of();
            fixture.refresh.run();
            assertNull(fixture.capture(true).mentions().find("@Renamed", 0));
        }
    }

    @Test
    void asyncScopesAndShutdownUseOnlyPublishedSnapshots() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.alice.staff = true;
            fixture.refresh.run();
            fixture.primary = false;
            ChatProcessingContext restricted = fixture.factory.capture(
                    fixture.sender.player, List.of(fixture.sender.player), false);
            assertNull(restricted.mentions().find("@Alice", 0));
            ChatProcessingContext staff = fixture.factory.capture(
                    fixture.sender.player, fixture.factory.staffPlayers(), false);
            assertEquals(fixture.alice.id, staff.mentions().find("@Alice", 0).target().id());
            var unknown = fixture.new TestPlayer("Unknown");
            assertFalse(fixture.factory.capture(unknown.player, List.of(), false)
                    .capabilities().contains(ChatCapability.PLAYER_MENTION));

            fixture.factory.close();
            assertTrue(fixture.cancelled);
            assertNull(fixture.factory.onlinePlayer(fixture.alice.id));
            assertTrue(fixture.factory.staffPlayers().isEmpty());
            assertFalse(fixture.capture(true).capabilities().contains(ChatCapability.PLAYER_MENTION));
        }
    }

    private static IdentityBinding binding(UUID playerId, String name, String account) {
        return new IdentityBinding(playerId, name,
                new ExternalIdentityKey("matrix", "example.org", "", "@" + account + ":example.org"),
                account, Instant.EPOCH, Instant.EPOCH);
    }

    private static final class Fixture implements AutoCloseable {
        private boolean primary = true;
        private boolean cancelled;
        private Runnable refresh;
        private List<IdentityBinding> bindings = List.of();
        private final TestPlayer sender = new TestPlayer("Sender");
        private final TestPlayer alice = new TestPlayer("Alice");
        private final List<TestPlayer> online = new ArrayList<>(List.of(sender, alice));
        private final Field serverField;
        private final Server previousServer;
        private final BukkitChatContextFactory factory;

        private Fixture() throws Exception {
            BukkitTask task = proxy(BukkitTask.class, (_, method, _) -> {
                if (method.getName().equals("cancel")) {
                    cancelled = true;
                    return null;
                }
                throw new AssertionError(method.getName());
            });
            BukkitScheduler scheduler = proxy(BukkitScheduler.class, (_, method, args) -> {
                if (method.getName().equals("runTaskTimer")) {
                    refresh = (Runnable) args[1];
                    return task;
                }
                throw new AssertionError(method.getName());
            });
            Server server = proxy(Server.class, (_, method, _) -> switch (method.getName()) {
                case "isPrimaryThread" -> primary;
                case "getOnlinePlayers" -> online.stream().map(player -> player.player).toList();
                case "getScheduler" -> scheduler;
                default -> throw new AssertionError(method.getName());
            });
            Plugin plugin = proxy(Plugin.class, (_, method, _) -> {
                if (method.getName().equals("getLogger")) return Logger.getAnonymousLogger();
                throw new AssertionError(method.getName());
            });
            IdentityBindingManager manager = proxy(IdentityBindingManager.class, (_, method, _) -> {
                if (method.getName().equals("bindings")) return bindings;
                throw new AssertionError(method.getName());
            });
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = Bukkit.getServer();
            serverField.set(null, server);
            factory = new BukkitChatContextFactory(plugin, manager);
            factory.enable();
        }

        private ChatProcessingContext capture(boolean includeBound) {
            return factory.capture(sender.player, online.stream().map(player -> player.player).toList(), includeBound);
        }

        @Override
        public void close() throws IllegalAccessException {
            try {
                primary = true;
                factory.close();
            } finally {
                serverField.set(null, previousServer);
            }
        }

        private final class TestPlayer {
            private final UUID id = UUID.randomUUID();
            private String name;
            private boolean staff;
            private final Player player;

            private TestPlayer(String name) {
                this.name = name;
                PlayerInventory inventory = proxy(PlayerInventory.class, (_, method, _) -> {
                    if (method.getName().equals("getItem") || method.getName().equals("getItemInMainHand")) return null;
                    throw new AssertionError(method.getName());
                });
                player = proxy(Player.class, (object, method, args) -> {
                    switch (method.getName()) {
                        case "equals": return object == args[0];
                        case "hashCode": return System.identityHashCode(object);
                        case "toString": return name;
                    }
                    if (!primary) throw new AssertionError("Async Bukkit player read: " + method.getName());
                    return switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> this.name;
                        case "isOnline" -> true;
                        case "hasPermission" -> staff && args[0].equals("group.moderator");
                        case "getInventory" -> inventory;
                        default -> throw new AssertionError(method.getName());
                    };
                });
            }
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
