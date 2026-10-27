package org.encinet.mik.feature.player.guidance;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TipStoreTest {
    private static final NamespacedKey KEY = new NamespacedKey("mik", "tip_history");
    private final PersistentDataContainer data = container();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> {
                if (method.getName().equals("getPersistentDataContainer")) return data;
                throw new UnsupportedOperationException(method.getName());
            });
    private final TipStore store = new TipStore(KEY);

    @Test
    void densityAndReminderIntervalsRoundTripInOnePlayerContainer() {
        assertTrue(store.load(player, 0).history().isEmpty());
        var snapshot = new TipState.Snapshot(Map.of(
                "home-return", new TipState.Entry("home-return", 2000, 3, 1.25, 1000),
                "dynamic", new TipState.Entry("target-a", 1500, 1, 0, 0),
                "hat-wear", TipState.Entry.empty().matched(2500, 5)));
        store.save(player, snapshot);

        assertEquals(snapshot, new TipStore(KEY).load(player, 3000));
        assertEquals(Set.of(KEY), data.getKeys(), "all tip data belongs to one feature-owned player key");
        var root = data.get(KEY, PersistentDataType.TAG_CONTAINER);
        assertArrayEquals(new long[]{1000, 320, 2000, 3},
                root.get(key("home-return"), PersistentDataType.LONG_ARRAY));
        assertFalse(root.has(key("home-return_situation")), "static text needs no redundant situation string");
        assertEquals("target-a", root.get(key("dynamic_situation"), PersistentDataType.STRING));
    }

    @Test
    void invalidEntriesDoNotHideValidHistoryOrOtherPlayerData() {
        var root = container();
        root.set(key("valid"), PersistentDataType.LONG_ARRAY, new long[]{1000, 256, 2000, 1});
        root.set(key("truncated"), PersistentDataType.LONG_ARRAY, new long[]{1000, 2});
        root.set(key("negative"), PersistentDataType.LONG_ARRAY, new long[]{-1, 256, 2000, 1});
        root.set(key("oversized"), PersistentDataType.LONG_ARRAY, new long[]{1000, Long.MAX_VALUE, 2000, 1});
        root.set(key("bad-count"), PersistentDataType.LONG_ARRAY, new long[]{1000, 256, 2000, Long.MAX_VALUE});
        data.set(KEY, PersistentDataType.TAG_CONTAINER, root);
        data.set(key("unrelated"), PersistentDataType.STRING, "kept");

        var loaded = store.load(player, 3000);
        assertEquals(Set.of("valid"), loaded.history().keySet());
        store.save(player, loaded);
        assertEquals("kept", data.get(key("unrelated"), PersistentDataType.STRING));
    }

    @Test
    void oldInactiveHistoryExpiresButFutureTimestampsDoNotDeleteRecentState() {
        var history = Map.of("expired", new TipState.Entry("expired", 1000, 6, 0, 1000),
                "future", new TipState.Entry("future", TipState.RETENTION * 2, 2, 1, 1000));
        store.save(player, new TipState.Snapshot(history));
        assertEquals(Set.of("future"), store.load(player, TipState.RETENTION + 1000).history().keySet());
        store.save(player, new TipState.Snapshot(Map.of()));
        assertFalse(data.has(KEY));
    }

    @Test
    void aFreshSchedulerUsesTheStoredDensityAndStillRecoversAfterAQuietPeriod() {
        long now = 1_800_000_000_000L;
        var rule = new Tip.Policy("chat", "chat", 0, Duration.ofDays(1), Duration.ZERO, Duration.ofSeconds(20), true);
        TipScheduler first = new TipScheduler(error -> fail(error), () -> 0, ignored -> TipAudience.MEMBER);
        var id = java.util.UUID.randomUUID();
        first.offer(this, id, rule, now, 1, true, 20, rule.delay(),
                language -> java.util.Optional.of(new TipContent("chat", net.kyori.adventure.text.Component.empty())));
        first.delivered(id, first.collect(id, now, 45_000, true, org.encinet.mik.shared.i18n.Language.EN_US), now);
        store.save(player, first.snapshot(id));

        TipScheduler restarted = new TipScheduler(error -> fail(error), () -> 0.5, ignored -> TipAudience.MEMBER);
        restarted.restore(id, store.load(player, now + 60_000));
        var stamp = restarted.snapshot(id).history().get("chat");
        assertTrue(stamp.probability(now + 60_000, 1) < 0.001);
        assertEquals(1, stamp.probability(now + Duration.ofHours(8).toMillis(), 1));
    }

    private static NamespacedKey key(String id) { return new NamespacedKey(KEY.getNamespace(), id); }

    private static PersistentDataContainer container() {
        Map<NamespacedKey, Value> values = new HashMap<>();
        PersistentDataAdapterContext context = () -> container();
        return (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAdapterContext" -> context;
                    case "getKeys" -> Set.copyOf(values.keySet());
                    case "has" -> values.containsKey(args[0])
                            && (args.length == 1 || values.get(args[0]).type == args[1]);
                    case "get", "getOrDefault" -> {
                        Value value = values.get(args[0]);
                        yield value != null && value.type == args[1] ? value.data : args.length == 3 ? args[2] : null;
                    }
                    case "set" -> {
                        Object value = args[2] instanceof long[] array ? array.clone() : args[2];
                        values.put((NamespacedKey) args[0], new Value(args[1], value));
                        yield null;
                    }
                    case "remove" -> { values.remove(args[0]); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private record Value(Object type, Object data) {}
}
