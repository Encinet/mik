package org.encinet.mik.feature.player.trash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TrashInventory implements InventoryHolder {
    static final int SIZE = 54;
    static final int SORT_SLOT = SIZE - 2;
    static final int CLEAR_SLOT = SIZE - 1;
    static final int STORAGE_SIZE = SIZE - 2;

    private final Inventory inventory;

    TrashInventory(Player player, LanguageService languageService) {
        inventory = Bukkit.createInventory(this, SIZE,
                languageService.text(player, Message.TRASH_TITLE, NamedTextColor.RED));
        inventory.setItem(SORT_SLOT, button(player, languageService, Material.HOPPER, Message.TRASH_SORT,
                Message.TRASH_SORT_HINT, NamedTextColor.AQUA));
        inventory.setItem(CLEAR_SLOT, button(player, languageService, Material.BARRIER, Message.TRASH_CLEAR,
                Message.TRASH_CLEAR_HINT, NamedTextColor.RED, TrashModule.RETENTION_SECONDS));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void clearStorage() {
        for (int slot = 0; slot < STORAGE_SIZE; slot++) inventory.setItem(slot, null);
    }

    boolean matchesButton(ItemStack item) {
        return item != null && (item.isSimilar(inventory.getItem(SORT_SLOT))
                || item.isSimilar(inventory.getItem(CLEAR_SLOT)));
    }

    void sortStorage() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            ItemStack source = inventory.getItem(slot);
            if (source == null || source.getType().isAir() || source.getAmount() <= 0) continue;
            ItemStack remaining = source.clone();
            int limit = Math.min(remaining.getMaxStackSize(), inventory.getMaxStackSize());
            for (ItemStack target : items) {
                if (!target.isSimilar(remaining)) continue;
                int moved = Math.min(remaining.getAmount(), limit - target.getAmount());
                if (moved <= 0) continue;
                target.setAmount(target.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
                if (remaining.getAmount() == 0) break;
            }
            while (remaining.getAmount() > 0) {
                ItemStack stack = remaining.clone();
                stack.setAmount(Math.min(remaining.getAmount(), limit));
                items.add(stack);
                remaining.setAmount(remaining.getAmount() - stack.getAmount());
            }
        }
        // Preserve unusual overstacked items if splitting them would exceed the available slots.
        if (items.size() > STORAGE_SIZE) return;
        items.sort(Comparator.comparing(item -> item.getType().name()));
        clearStorage();
        for (int slot = 0; slot < items.size(); slot++) inventory.setItem(slot, items.get(slot));
    }

    private static ItemStack button(Player player, LanguageService languageService, Material material,
                                    Message name, Message hint, NamedTextColor color, Object... hintArgs) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(languageService.text(player, name, color)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(languageService.t(player, hint, hintArgs).lines()
                    .map(line -> Component.text(line, NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false))
                    .toList());
            // Full, unstackable buttons cannot receive items moved with shift-click.
            meta.setMaxStackSize(1);
        });
        return item;
    }
}
