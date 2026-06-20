package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MenuBuilder {

    private final Inventory inventory;

    private MenuBuilder(int size, Component title) {
        this.inventory = Bukkit.createInventory(null, size, title);
    }

    public static MenuBuilder create(int size, Component title) {
        return new MenuBuilder(size, title);
    }

    public MenuBuilder item(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        return this;
    }

    public Inventory inventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}
