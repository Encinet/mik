package org.encinet.mik.feature.world.heads;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.encinet.mik.feature.world.heads.catalog.HeadEntry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Converts trusted catalog entries to ordinary, placeable player heads on the server thread. */
final class HeadItems {
    static final int MAX_AMOUNT = 64;
    private static final String TEXTURES = "https://textures.minecraft.net/texture/";

    private HeadItems() { }

    static ItemStack preview(HeadEntry entry) {
        return entry.texture() == null ? new ItemStack(Material.PLAYER_HEAD) : item(entry);
    }

    static boolean give(Player player, HeadEntry entry, int amount) {
        if (amount < 1 || amount > MAX_AMOUNT) throw new IllegalArgumentException("Invalid head amount");
        if (entry.texture() == null && entry.playerId() == null) throw new IllegalArgumentException("Head texture is missing");
        return insert(player.getInventory(), item(entry), amount);
    }

    static boolean insert(PlayerInventory inventory, ItemStack item, int amount) {
        int maximum = Math.min(inventory.getMaxStackSize(), item.getMaxStackSize());
        ItemStack[] slots = inventory.getStorageContents().clone();
        int remaining = amount;
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            ItemStack slot = slots[i];
            if (slot == null || slot.isEmpty() || !slot.isSimilar(item) || slot.getAmount() >= maximum) continue;
            int moved = Math.min(maximum - slot.getAmount(), remaining);
            slots[i] = slot.asQuantity(slot.getAmount() + moved);
            remaining -= moved;
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) continue;
            int moved = Math.min(remaining, maximum);
            slots[i] = item.asQuantity(moved);
            remaining -= moved;
        }
        if (remaining != 0) return false;
        inventory.setStorageContents(slots);
        return true;
    }

    private static ItemStack item(HeadEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        var playerId = entry.playerId();
        item.editMeta(SkullMeta.class, meta -> {
            var profile = playerId == null
                    ? Bukkit.createProfileExact(UUID.nameUUIDFromBytes(("mik:head:" + entry.texture()).getBytes(StandardCharsets.UTF_8)), "DecorativeHead")
                    : Bukkit.createProfileExact(playerId, entry.name());
            if (entry.texture() != null) {
                var textures = profile.getTextures();
                try { textures.setSkin(URI.create(TEXTURES + entry.texture()).toURL()); }
                catch (java.net.MalformedURLException error) { throw new IllegalStateException(error); }
                profile.setTextures(textures);
            }
            meta.setPlayerProfile(profile);
            meta.displayName(Component.text(entry.name()).decoration(TextDecoration.ITALIC, false));
        });
        return item;
    }
}
