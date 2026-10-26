package org.encinet.mik.feature.player.guidance;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/** Main-thread codec for one player's PDC; Paper owns disk persistence and the player lifecycle. */
final class TipStore {
    private final NamespacedKey key;

    TipStore(NamespacedKey key) { this.key = key; }

    TipState.Snapshot load(Player player, long now) {
        var root = player.getPersistentDataContainer().get(key, PersistentDataType.TAG_CONTAINER);
        if (root == null) return new TipState.Snapshot(Map.of());
        Map<String, TipState.Entry> history = new HashMap<>();
        for (NamespacedKey entry : root.getKeys()) {
            String id = entry.getKey();
            if (!entry.getNamespace().equals(key.getNamespace()) || !id.matches("[a-z][a-z0-9-]{0,63}")) continue;
            long[] values = root.get(entry, PersistentDataType.LONG_ARRAY);
            if (values == null || values.length != 4 || values[1] < 0
                    || values[1] > TipState.MAX_PRESSURE * TipState.PRESSURE_PRECISION
                    || values[3] < 0 || values[3] > TipState.MAX_PRESENTATIONS) continue;
            String situation = values[3] == 0 ? "" : root.getOrDefault(situationKey(id), PersistentDataType.STRING, id);
            try {
                var stamp = new TipState.Entry(situation, values[2], (int) values[3],
                        (double) values[1] / TipState.PRESSURE_PRECISION, values[0]);
                if (!stamp.expired(now)) history.put(id, stamp);
            } catch (IllegalArgumentException invalid) {
                // An invalid entry cannot prevent this player's other scenarios from loading.
            }
        }
        return new TipState.Snapshot(history);
    }

    void save(Player player, TipState.Snapshot snapshot) {
        var data = player.getPersistentDataContainer();
        if (snapshot.history().isEmpty()) {
            data.remove(key);
            return;
        }
        var root = data.getAdapterContext().newPersistentDataContainer();
        snapshot.history().forEach((id, stamp) -> {
            root.set(new NamespacedKey(key.getNamespace(), id), PersistentDataType.LONG_ARRAY,
                    new long[]{stamp.matchedAt(), Math.round(stamp.pressure() * TipState.PRESSURE_PRECISION),
                            stamp.sentAt(), stamp.presentations()});
            if (stamp.presentations() > 0 && !stamp.situation().equals(id)) {
                root.set(situationKey(id), PersistentDataType.STRING, stamp.situation());
            }
        });
        data.set(key, PersistentDataType.TAG_CONTAINER, root);
    }

    private NamespacedKey situationKey(String id) {
        return new NamespacedKey(key.getNamespace(), id + "_situation");
    }
}
