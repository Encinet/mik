package org.encinet.mik.module.musicdisc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for jukebox control panel with left-right layout
 */
public class JukeboxControlGUI {

    private static final int GUI_SIZE = 54;
    private static final int CURRENT_PLAYING_SLOT = 4;
    private static final int[] PLAYLIST_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    }; // 27 slots for playlist (rows 1-3)

    private final JukeboxPlaylistManager playlistManager;
    private final MusicDiscCreator discCreator;
    private final Map<UUID, Location> playerJukeboxMap = new HashMap<>();

    public JukeboxControlGUI(JukeboxPlaylistManager playlistManager, MusicDiscCreator discCreator) {
        this.playlistManager = playlistManager;
        this.discCreator = discCreator;
    }

    /**
     * Open jukebox control GUI
     */
    public void openJukeboxControl(Player player, Jukebox jukebox) {
        Location location = jukebox.getLocation();
        playerJukeboxMap.put(player.getUniqueId(), location);

        Inventory inv = createJukeboxControlInventory(jukebox);
        player.openInventory(inv);
    }

    /**
     * Get jukebox location for player
     */
    public Location getPlayerJukebox(UUID uuid) {
        return playerJukeboxMap.get(uuid);
    }

    /**
     * Remove player jukebox mapping
     */
    public void removePlayerJukebox(UUID uuid) {
        playerJukeboxMap.remove(uuid);
    }

    /**
     * Check if slot is a playlist slot
     */
    public boolean isPlaylistSlot(int slot) {
        for (int playlistSlot : PLAYLIST_SLOTS) {
            if (playlistSlot == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create jukebox control inventory with new layout
     */
    private Inventory createJukeboxControlInventory(Jukebox jukebox) {
        Location location = jukebox.getLocation();
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);

        String title = String.format("唱片机控制 (%d, %d, %d)",
                location.getBlockX(), location.getBlockY(), location.getBlockZ());

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
                Component.text(title).color(NamedTextColor.DARK_PURPLE));

        // Row 0: Current playing and music selection button
        MusicFileLoader.MusicFile currentDisc = playlistManager.getCurrentDisc(jukebox);
        if (currentDisc != null) {
            ItemStack discItem = discCreator.createMusicDisc(currentDisc, false);
            ItemMeta meta = discItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("当前播放")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                discItem.setItemMeta(meta);
            }
            inv.setItem(CURRENT_PLAYING_SLOT, discItem);
        } else {
            inv.setItem(CURRENT_PLAYING_SLOT, createInfoItem(Material.BARRIER, "当前播放", "无唱片"));
        }

        // Music selection button (slot 8) - only in sequential mode
        if (!data.isRandomMode) {
            inv.setItem(8, createMusicSelectionButton());
        }

        // Rows 1-3: Playlist area (only in sequential mode)
        if (!data.isRandomMode) {
            for (int i = 0; i < Math.min(data.playlist.size(), PLAYLIST_SLOTS.length); i++) {
                MusicFileLoader.MusicFile music = data.playlist.get(i);
                ItemStack discItem = createPlaylistDiscItem(music, i + 1);
                inv.setItem(PLAYLIST_SLOTS[i], discItem);
            }
        } else {
            // Fill with disabled items in random mode
            ItemStack disabledItem = createDisabledPlaylistItem();
            for (int slot : PLAYLIST_SLOTS) {
                inv.setItem(slot, disabledItem);
            }
        }

        // Rows 4-5: Control buttons
        // Row 4: Mode, Auto-play, Play next
        inv.setItem(38, createPlayModeButton(data.isRandomMode));
        inv.setItem(40, createAutoPlayButton(data.autoPlay));
        inv.setItem(42, createPlayNextButton());

        // Row 5: Add all, Clear, Close
        if (!data.isRandomMode) {
            inv.setItem(46, createAddAllButton());
            inv.setItem(48, createClearPlaylistButton());
        }
        inv.setItem(53, createCloseButton());

        return inv;
    }

    private ItemStack createPlaylistDiscItem(MusicFileLoader.MusicFile music, int rank) {
        ItemStack discItem = discCreator.createMusicDisc(music, false);
        ItemMeta meta = discItem.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(Component.text(""));
            lore.add(Component.text("排名: #" + rank)
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));
            lore.add(Component.text("左键: 播放并移除")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("右键: 仅移除")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Shift+左键: 提升排名")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Shift+右键: 降低排名")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            discItem.setItemMeta(meta);
        }
        return discItem;
    }

    private ItemStack createMusicSelectionButton() {
        ItemStack button = new ItemStack(Material.MUSIC_DISC_WAIT);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("选择音乐")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Component.text("点击打开音乐选择界面")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            button.setItemMeta(meta);
        }
        return button;
    }

    private ItemStack createDisabledPlaylistItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("随机模式")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("播放列表在随机模式下不可用")
                            .color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPlayModeButton(boolean isRandomMode) {
        Material material = isRandomMode ? Material.PURPLE_DYE : Material.LIME_DYE;
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            String modeName = isRandomMode ? "随机模式" : "列表模式";
            meta.displayName(Component.text(modeName)
                    .color(isRandomMode ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("当前模式: " + modeName)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));

            if (isRandomMode) {
                lore.add(Component.text("从所有歌曲中随机播放")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("按顺序播放列表中的歌曲")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("点击切换模式")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createAutoPlayButton(boolean enabled) {
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("自动播放")
                    .color(enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("状态: " + (enabled ? "开启" : "关闭"))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));
            lore.add(Component.text("歌曲结束后自动播放下一首")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("点击切换")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createPlayNextButton() {
        ItemStack button = new ItemStack(Material.ARROW);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("播放下一首")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text("从播放列表播放下一首歌曲")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createAddAllButton() {
        ItemStack button = new ItemStack(Material.CHEST);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("添加所有歌曲")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text("将所有可用歌曲添加到播放列表")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createClearPlaylistButton() {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("清空播放列表")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text("清空当前播放列表")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createCloseButton() {
        ItemStack button = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("关闭")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createInfoItem(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text(description)
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            item.setItemMeta(meta);
        }

        return item;
    }
}
