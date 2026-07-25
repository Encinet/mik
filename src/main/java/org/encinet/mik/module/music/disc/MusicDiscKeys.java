package org.encinet.mik.module.music.disc;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MusicDiscKeys {

    public static final NamespacedKey TRACK = new NamespacedKey("mik", "music_track");
    public static final NamespacedKey TRACK_DATA = new NamespacedKey("mik", "music_track_data");
    public static final NamespacedKey TRACK_SIGNATURE = new NamespacedKey("mik", "music_track_signature");
    public static final NamespacedKey INTERNAL = new NamespacedKey("mik", "music_internal");

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

    public static boolean isInternal(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Byte value = meta == null ? null : meta.getPersistentDataContainer()
                .get(INTERNAL, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    public static void markInternal(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(INTERNAL, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }
}
