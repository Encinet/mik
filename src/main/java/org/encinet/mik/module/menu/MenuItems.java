package org.encinet.mik.module.menu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

public final class MenuItems {

    public static final TextColor TITLE_COLOR = TextColor.color(0xC86A1D);

    private MenuItems() {
    }

    public static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(name));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(MenuItems::plain).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack action(Material material, Component name, List<Component> lore, NamespacedKey actionKey, String action) {
        ItemStack item = item(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack actionWithoutDiscTrack(Material material, Component name, List<Component> lore,
                                                   NamespacedKey actionKey, String action) {
        ItemStack item = action(material, name, lore, actionKey, action);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.JUKEBOX_PLAYABLE)
                .build());
        return item;
    }

    public static ItemStack filler(Material material) {
        return item(material, Component.empty(), List.of());
    }

    public static String readAction(ItemStack item, NamespacedKey actionKey) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.get(actionKey, PersistentDataType.STRING);
    }

    public static void border(Inventory inventory, Material material) {
        ItemStack filler = filler(material);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == inventory.getSize() / 9 - 1 || column == 0 || column == 8) {
                inventory.setItem(slot, filler);
            }
        }
    }

    public static void fillExcept(Inventory inventory, Material material, int[] emptySlots) {
        ItemStack filler = filler(material);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        Arrays.stream(emptySlots).forEach(inventory::clear);
    }

    public static void style(ItemMeta meta) {
        if (meta.hasDisplayName()) {
            meta.displayName(plain(meta.displayName()));
        }
        List<Component> lore = meta.lore();
        if (lore != null) {
            meta.lore(lore.stream().map(MenuItems::plain).toList());
        }
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
