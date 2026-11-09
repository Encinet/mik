package org.encinet.mik.feature.entertainment.music.disc;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MusicDiscKeys {

    public static final NamespacedKey TRACK = new NamespacedKey("mik", "music_v2_track");
    public static final NamespacedKey TRACK_DATA = new NamespacedKey("mik", "music_v2_data");
    public static final NamespacedKey TRACK_SIGNATURE = new NamespacedKey("mik", "music_v2_signature");

    private MusicDiscKeys() {
    }

    public static String trackId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(TRACK, PersistentDataType.STRING);
    }

    public static boolean isCustomDisc(ItemStack item) {
        return trackId(item) != null;
    }

    public static String trackData(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer()
                .get(TRACK_DATA, PersistentDataType.STRING);
    }

    public static String trackSignature(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer()
                .get(TRACK_SIGNATURE, PersistentDataType.STRING);
    }

}
