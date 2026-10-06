package org.encinet.mik.module.music.rhythm;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmCalibrationStoreTest {
    @Test
    void savesAndClearsTheCompletePlayerCalibration() {
        Map<NamespacedKey, Integer> values = new HashMap<>();
        RhythmCalibrationStore store = new RhythmCalibrationStore(plugin());
        Player player = player(data(values));

        assertTrue(store.read(player).isEmpty());
        assertEquals(0, store.profiles(player).minecraft().judgementOffsetMillis());
        assertEquals(0, store.pointerInputDelta(player));

        RhythmCalibrationResult result = RhythmCalibrationResult.fromTests(
                55, 100, 145, 73);
        store.save(player, result);
        assertEquals(5, values.size());
        assertEquals(result, store.read(player).orElseThrow());
        assertEquals(18, store.pointerInputDelta(player));

        values.keySet().removeIf(key -> key.getKey().equals("rhythm_plasmo_animation_ms"));
        assertTrue(store.read(player).isEmpty());

        store.clear(player);
        assertTrue(values.isEmpty());
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")
                            || method.getName().equals("namespace")) return "mik";
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player player(PersistentDataContainer data) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPersistentDataContainer")) return data;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static PersistentDataContainer data(Map<NamespacedKey, Integer> values) {
        return (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "get" -> values.get(args[0]);
                        case "set" -> {
                            values.put((NamespacedKey) args[0], (Integer) args[2]);
                            yield null;
                        }
                        case "remove" -> {
                            values.remove(args[0]);
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }
}
