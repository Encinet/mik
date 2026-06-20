package org.encinet.mik.module.musicdisc;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for music disc inventory
 */
public class MusicInventoryGUI {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int GUI_SIZE = 54;

    private final MusicFileLoader musicFileLoader;
    private final MusicDiscCreator discCreator;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerSearchKeywords = new HashMap<>();
    private final Map<UUID, Location> playerJukeboxContext = new HashMap<>();

    public MusicInventoryGUI(MusicFileLoader musicFileLoader, MusicDiscCreator discCreator) {
        this.musicFileLoader = musicFileLoader;
        this.discCreator = discCreator;
    }

    /**
     * Open music inventory for player
     */
    public void openMusicInventory(Player player) {
        openMusicInventory(player, null, 0);
    }

    /**
     * Open music inventory for player with search keyword
     */
    public void openMusicInventory(Player player, String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            long matchCount = musicFileLoader.getMusicFiles().stream()
                    .filter(music -> music.displayName().toLowerCase().contains(lowerKeyword)
                            || music.fileName().toLowerCase().contains(lowerKeyword))
                    .count();

            if (matchCount == 0) {
                player.sendMessage(Component.text()
                        .append(Component.text("未找到包含 \"").color(NamedTextColor.RED))
                        .append(Component.text(keyword).color(NamedTextColor.YELLOW))
                        .append(Component.text("\" 的歌曲").color(NamedTextColor.RED))
                        .build());
                return;
            }
        }

        openMusicInventory(player, keyword, 0);
    }

    /**
     * Open music inventory for player at specific page
     */
    public void openMusicInventory(Player player, int page) {
        openMusicInventory(player, null, page);
    }

    /**
     * Open music inventory for player with search keyword at specific page
     */
    public void openMusicInventory(Player player, String keyword, int page) {
        UUID uuid = player.getUniqueId();
        playerPages.put(uuid, page);

        if (keyword != null && !keyword.isEmpty()) {
            playerSearchKeywords.put(uuid, keyword);
        } else {
            playerSearchKeywords.remove(uuid);
        }

        Inventory inv = createMusicInventory(player, page);
        player.openInventory(inv);
    }

    /**
     * Get player's current page
     */
    public Integer getPlayerPage(UUID uuid) {
        return playerPages.get(uuid);
    }

    /**
     * Get player's search keyword
     */
    public String getPlayerSearchKeyword(UUID uuid) {
        return playerSearchKeywords.get(uuid);
    }

    /**
     * Remove player data
     */
    public void removePlayerData(UUID uuid) {
        playerPages.remove(uuid);
        playerSearchKeywords.remove(uuid);
        playerJukeboxContext.remove(uuid);
    }

    /**
     * Set jukebox context for player
     */
    public void setJukeboxContext(UUID uuid, Location jukeboxLocation) {
        playerJukeboxContext.put(uuid, jukeboxLocation);
    }

    /**
     * Get jukebox context for player
     */
    public Location getJukeboxContext(UUID uuid) {
        return playerJukeboxContext.get(uuid);
    }

    /**
     * Get filtered music files based on keyword
     */
    public List<MusicFileLoader.MusicFile> getFilteredFiles(String keyword) {
        List<MusicFileLoader.MusicFile> files = musicFileLoader.getMusicFiles();
        if (keyword != null && !keyword.isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            return files.stream()
                    .filter(music -> music.displayName().toLowerCase().contains(lowerKeyword)
                            || music.fileName().toLowerCase().contains(lowerKeyword))
                    .toList();
        }
        return files;
    }

    /**
     * Create music disc inventory GUI
     */
    private Inventory createMusicInventory(Player player, int page) {
        UUID uuid = player.getUniqueId();
        String keyword = playerSearchKeywords.get(uuid);
        Location jukeboxContext = playerJukeboxContext.get(uuid);

        List<MusicFileLoader.MusicFile> filteredFiles = getFilteredFiles(keyword);

        int totalPages = (int) Math.ceil((double) filteredFiles.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = "Music Discs - Page " + (page + 1) + "/" + totalPages;
        if (keyword != null && !keyword.isEmpty()) {
            title = "搜索: " + keyword + " - " + (page + 1) + "/" + totalPages;
        }

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
                Component.text(title).color(NamedTextColor.DARK_PURPLE));

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredFiles.size());

        for (int i = startIndex; i < endIndex; i++) {
            MusicFileLoader.MusicFile music = filteredFiles.get(i);
            ItemStack disc;

            if (jukeboxContext != null) {
                // In jukebox context, show different lore
                disc = discCreator.createMusicDisc(music, false);
                ItemMeta meta = disc.getItemMeta();
                if (meta != null) {
                    List<Component> lore = meta.lore();
                    if (lore == null) {
                        lore = new ArrayList<>();
                    }
                    lore.add(Component.text(""));
                    lore.add(Component.text("点击添加到播放列表")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    disc.setItemMeta(meta);
                }
            } else {
                // Normal mode
                disc = discCreator.createMusicDisc(music);
            }

            inv.setItem(i - startIndex, disc);
        }

        if (page > 0) {
            inv.setItem(45, createNavigationButton(Material.ARROW, "上一页", "Previous Page"));
        }

        if (page < totalPages - 1) {
            inv.setItem(53, createNavigationButton(Material.ARROW, "下一页", "Next Page"));
        }

        inv.setItem(47, createSearchButton());
        inv.setItem(46, createRandomDiscButton(jukeboxContext != null));
        inv.setItem(51, createHelpButton(player));

        if (keyword != null && !keyword.isEmpty()) {
            inv.setItem(48, createClearSearchButton());
        }

        // Add back button if in jukebox context
        if (jukeboxContext != null) {
            inv.setItem(52, createBackButton());
        }

        inv.setItem(49, createPageInfo(page + 1, totalPages, filteredFiles.size()));

        return inv;
    }

    private ItemStack createNavigationButton(Material material, String name, String lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(Component.text(lore)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createPageInfo(int currentPage, int totalPages, int totalResults) {
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("第 " + currentPage + " 页 / 共 " + totalPages + " 页")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("Page " + currentPage + " of " + totalPages)
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("共 " + totalResults + " 首歌曲")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            info.setItemMeta(meta);
        }

        return info;
    }

    private ItemStack createSearchButton() {
        ItemStack button = new ItemStack(Material.COMPASS);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("搜索歌曲")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("点击后输入")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("/music search <关键词>")
                            .color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("来搜索歌曲")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createClearSearchButton() {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("清除搜索")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(Component.text("点击返回完整列表")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createRandomDiscButton(boolean isJukeboxContext) {
        ItemStack button = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            if (isJukeboxContext) {
                meta.displayName(Component.text("随机添加歌曲")
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));

                meta.lore(List.of(
                        Component.text("点击随机添加一首歌曲到播放列表")
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                meta.displayName(Component.text("随机唱片")
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));

                meta.lore(List.of(
                        Component.text("左键: 获得一张随机音乐唱片")
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("右键: 在最近的唱片机播放")
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("").decoration(TextDecoration.ITALIC, false),
                        Component.text("命令:")
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("/music random")
                                .color(NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("/music randomplay")
                                .color(NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            }

            button.setItemMeta(meta);

            TooltipDisplay tooltipDisplay = TooltipDisplay.tooltipDisplay()
                    .addHiddenComponents(
                            DataComponentTypes.JUKEBOX_PLAYABLE, DataComponentTypes.ENCHANTMENTS,
                            DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE,
                            DataComponentTypes.CAN_BREAK, DataComponentTypes.CAN_PLACE_ON,
                            DataComponentTypes.STORED_ENCHANTMENTS, DataComponentTypes.DYED_COLOR,
                            DataComponentTypes.TRIM
                    )
                    .build();

            button.setData(DataComponentTypes.TOOLTIP_DISPLAY, tooltipDisplay);
        }

        return button;
    }

    private ItemStack createHelpButton(Player player) {
        ItemStack button = new ItemStack(Material.BOOK);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("命令帮助")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("可用命令:")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  打开音乐界面").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music search <关键词>").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  搜索歌曲").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music page <页码>").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  跳转到指定页面").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music random").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  获得随机音乐唱片").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music randomplay").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  在最近的唱片机播放随机音乐").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            if (player.hasPermission("group.manager")) {
                lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("/music reload").color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  重新加载歌曲列表 (管理员)").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createBackButton() {
        ItemStack button = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("返回唱片机控制")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text("点击返回唱片机控制界面")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }
}
