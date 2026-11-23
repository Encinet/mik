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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
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
            ChatProcessingContext first = fixture.capture();
            assertSame(first.mentions().online(), fixture.capture().mentions().online());
            fixture.refresh.run();
            assertSame(first.mentions().online(), fixture.capture().mentions().online());

            fixture.alice.name = "Alex";
            ChatProcessingContext renamed = fixture.capture();
            assertNotSame(first.mentions().online(), renamed.mentions().online());
            assertNull(renamed.mentions().find("@Alice", 0));
            assertEquals(fixture.alice.id, renamed.mentions().find("@Alex", 0).target().id());
            assertEquals(fixture.alice.id, first.mentions().find("@Alice", 0).target().id());

            fixture.primary = false;
            ChatProcessingContext async = fixture.capture();
            assertSame(renamed.mentions().online(), async.mentions().online());
            assertEquals(fixture.alice.id, async.mentions().find("@ Alex", 0).target().id());
            assertSame(fixture.alice.player, fixture.factory.onlinePlayer(fixture.alice.id));
        }
    }

    @Test
    void asyncScopesAndShutdownUseOnlyPublishedSnapshots() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.alice.staff = true;
            fixture.refresh.run();
            fixture.primary = false;
            ChatProcessingContext restricted = fixture.factory.capture(
                    fixture.sender.player, List.of(fixture.sender.player));
            assertNull(restricted.mentions().find("@Alice", 0));
            ChatProcessingContext staff = fixture.factory.capture(
                    fixture.sender.player, fixture.factory.staffPlayers());
            assertEquals(fixture.alice.id, staff.mentions().find("@Alice", 0).target().id());
            var unknown = fixture.new TestPlayer("Unknown");
            assertFalse(fixture.factory.capture(unknown.player, List.of())
                    .capabilities().contains(ChatCapability.PLAYER_MENTION));

            fixture.factory.close();
            assertTrue(fixture.cancelled);
            assertNull(fixture.factory.onlinePlayer(fixture.alice.id));
            assertTrue(fixture.factory.staffPlayers().isEmpty());
            assertFalse(fixture.capture().capabilities().contains(ChatCapability.PLAYER_MENTION));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private boolean primary = true;
        private boolean cancelled;
        private Runnable refresh;
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
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = Bukkit.getServer();
            serverField.set(null, server);
            factory = new BukkitChatContextFactory(plugin);
            factory.enable();
        }

        private ChatProcessingContext capture() {
            return factory.capture(sender.player, online.stream().map(player -> player.player).toList());
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
