package org.encinet.mik.feature.entertainment.music.disc;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MusicDiscKeys {

    public static final NamespacedKey TRACK = new NamespacedKey("mik", "music_v3_track");
    public static final NamespacedKey TRACK_SIGNATURE = new NamespacedKey("mik", "music_v3_signature");

    static String signedIdentity(String id) { return "mik-disc:3:" + id; }

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

    public static String trackSignature(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer()
                .get(TRACK_SIGNATURE, PersistentDataType.STRING);
    }

}
