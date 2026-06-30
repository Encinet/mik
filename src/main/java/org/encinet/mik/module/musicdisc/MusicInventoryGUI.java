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
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;

import java.util.*;

/**
 * GUI for music disc inventory
 */
public class MusicInventoryGUI {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int GUI_SIZE = 54;

    private final MusicFileLoader musicFileLoader;
    private final MusicDiscCreator discCreator;
    private final LanguageService languageService;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerSearchKeywords = new HashMap<>();
    private final Map<UUID, Location> playerJukeboxContext = new HashMap<>();

    public MusicInventoryGUI(MusicFileLoader musicFileLoader, MusicDiscCreator discCreator,
                             LanguageService languageService) {
        this.musicFileLoader = musicFileLoader;
        this.discCreator = discCreator;
        this.languageService = languageService;
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
                player.sendMessage(languageService.rich(player, Message.MUSIC_NO_SEARCH_RESULTS_RICH,
                        NamedTextColor.RED,
                        RichArg.component("keyword", Component.text(keyword, NamedTextColor.YELLOW), keyword)));
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

        String title = languageService.t(player, Message.MUSIC_MENU_TITLE, page + 1, totalPages);
        if (keyword != null && !keyword.isEmpty()) {
            title = languageService.t(player, Message.MUSIC_MENU_SEARCH_TITLE, keyword, page + 1, totalPages);
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
                disc = discCreator.createMusicDisc(music, false, player);
                ItemMeta meta = disc.getItemMeta();
                if (meta != null) {
                    List<Component> lore = meta.lore();
                    if (lore == null) {
                        lore = new ArrayList<>();
                    }
                    lore.add(Component.text(""));
                    lore.add(Component.text(languageService.t(player, Message.MUSIC_DISC_ADD_TO_PLAYLIST_LORE))
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    disc.setItemMeta(meta);
                }
            } else {
                // Normal mode
                disc = discCreator.createMusicDisc(music, player);
            }

            inv.setItem(i - startIndex, disc);
        }

        if (page > 0) {
            inv.setItem(45, createNavigationButton(player, Material.ARROW,
                    Message.MUSIC_PREV_PAGE, Message.MUSIC_PREV_PAGE_LORE));
        }

        if (page < totalPages - 1) {
            inv.setItem(53, createNavigationButton(player, Material.ARROW,
                    Message.MUSIC_NEXT_PAGE, Message.MUSIC_NEXT_PAGE_LORE));
        }

        inv.setItem(47, createSearchButton(player));
        inv.setItem(46, createRandomDiscButton(player, jukeboxContext != null));
        inv.setItem(51, createHelpButton(player));

        if (keyword != null && !keyword.isEmpty()) {
            inv.setItem(48, createClearSearchButton(player));
        }

        // Add back button if in jukebox context
        if (jukeboxContext != null) {
            inv.setItem(52, createBackButton(player));
        }

        inv.setItem(49, createPageInfo(player, page + 1, totalPages, filteredFiles.size()));

        return inv;
    }

    private ItemStack createNavigationButton(Player player, Material material, Message name, Message lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, name))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(Component.text(languageService.t(player, lore))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createPageInfo(Player player, int currentPage, int totalPages, int totalResults) {
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_PAGE_INFO, currentPage, totalPages))
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_PAGE_TOTAL, totalResults))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            info.setItemMeta(meta);
        }

        return info;
    }

    private ItemStack createSearchButton(Player player) {
        ItemStack button = new ItemStack(Material.COMPASS);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_SEARCH_BUTTON))
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_SEARCH_LORE_COMMAND, "/music search <keyword>"))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createClearSearchButton(Player player) {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_CLEAR_SEARCH))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(Component.text(languageService.t(player, Message.MUSIC_CLEAR_SEARCH_LORE))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createRandomDiscButton(Player player, boolean isJukeboxContext) {
        ItemStack button = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            if (isJukeboxContext) {
                meta.displayName(Component.text(languageService.t(player, Message.MUSIC_RANDOM_ADD))
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));

                meta.lore(List.of(
                        Component.text(languageService.t(player, Message.MUSIC_RANDOM_ADD_LORE))
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                meta.displayName(Component.text(languageService.t(player, Message.MUSIC_RANDOM_DISC))
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));

                meta.lore(List.of(
                        Component.text(languageService.t(player, Message.MUSIC_RANDOM_DISC_LEFT))
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text(languageService.t(player, Message.MUSIC_RANDOM_DISC_RIGHT))
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("").decoration(TextDecoration.ITALIC, false),
                        Component.text(languageService.t(player, Message.MUSIC_COMMANDS_LABEL))
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
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_HELP_TITLE))
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(languageService.t(player, Message.MUSIC_HELP_AVAILABLE_COMMANDS))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("   " + languageService.t(player, Message.MUSIC_HELP_OPEN_DESC)).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music search <keyword>").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("   " + languageService.t(player, Message.MUSIC_HELP_SEARCH_DESC)).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music page <page>").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("   " + languageService.t(player, Message.MUSIC_HELP_PAGE_DESC)).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music random").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("   " + languageService.t(player, Message.MUSIC_HELP_RANDOM_DESC)).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music randomplay").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("   " + languageService.t(player, Message.MUSIC_HELP_RANDOMPLAY_DESC)).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            if (player.hasPermission("group." + Mik.GROUP_MANAGER)) {
                lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("/music reload").color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("   " + languageService.t(player, Message.MUSIC_HELP_RELOAD_DESC)).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createBackButton(Player player) {
        ItemStack button = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_BACK_JUKEBOX))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_BACK_JUKEBOX_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }
}
