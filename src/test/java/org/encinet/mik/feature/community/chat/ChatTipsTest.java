package org.encinet.mik.feature.community.chat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChatTipsTest {
    @Test
    void publicPrivateCommandsAccumulateAcrossConnectionsWithoutRepeatedWritesOrStaleGuidance() {
        Map<NamespacedKey, Byte> saved = new HashMap<>();
        AtomicInteger writes = new AtomicInteger();
        UUID id = UUID.randomUUID();
        Player player = player(id, saved, writes);
        AtomicBoolean publicChannel = new AtomicBoolean(false);
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("namespace")) return "mik";
                    throw new UnsupportedOperationException(method.getName());
                });

        try (TipTestScope tips = new TipTestScope()) {
            var sent = ChatTips.privateChannel(plugin, tips, ignored -> publicChannel.get());
            sent.accept(player);
            assertTrue(saved.isEmpty(), "private or staff channel uses must not count");
            publicChannel.set(true);
            sent.accept(player);
            assertEquals(1, writes.get());
            assertTrue(tips.collect(player, 2000).isEmpty(), "the first use must not offer a tip");
        }

        player = player(id, saved, writes);
        try (TipTestScope tips = new TipTestScope()) {
            var sent = ChatTips.privateChannel(plugin, tips, ignored -> publicChannel.get());
            sent.accept(player);
            assertEquals(2, writes.get(), "the previous connection's first use must be retained");
            assertEquals((byte) 2, saved.get(new NamespacedKey("mik", "chat_private_command_uses")));
            publicChannel.set(false);
            assertTrue(tips.collect(player, 2000).isEmpty(), "changing channels suppresses a queued tip");

            publicChannel.set(true);
            sent.accept(player);
            assertEquals(2, writes.get(), "later uses must not grow or rewrite the saturated counter");
            assertEquals(1, tips.deliver(player, 4000).size());
            sent.accept(player);
            assertTrue(tips.collect(player, 6000).isEmpty(), "normal reminder intervals still apply");
        }
    }

    private static Player player(UUID id, Map<NamespacedKey, Byte> saved, AtomicInteger writes) {
        PersistentDataContainer data = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(), new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOrDefault" -> saved.getOrDefault(args[0], (Byte) args[2]);
                    case "set" -> {
                        saved.put((NamespacedKey) args[0], (Byte) args[2]);
                        writes.incrementAndGet();
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getPersistentDataContainer" -> data;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
