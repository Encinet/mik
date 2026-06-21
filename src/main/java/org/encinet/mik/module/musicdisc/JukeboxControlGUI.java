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
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

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
    private final LanguageService languageService;
    private final Map<UUID, Location> playerJukeboxMap = new HashMap<>();

    public JukeboxControlGUI(JukeboxPlaylistManager playlistManager, MusicDiscCreator discCreator,
                             LanguageService languageService) {
        this.playlistManager = playlistManager;
        this.discCreator = discCreator;
        this.languageService = languageService;
    }

    /**
     * Open jukebox control GUI
     */
    public void openJukeboxControl(Player player, Jukebox jukebox) {
        Location location = jukebox.getLocation();
        playerJukeboxMap.put(player.getUniqueId(), location);

        Inventory inv = createJukeboxControlInventory(player, jukebox);
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
    private Inventory createJukeboxControlInventory(Player player, Jukebox jukebox) {
        Location location = jukebox.getLocation();
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);

        String title = languageService.t(player, Message.MUSIC_JUKEBOX_TITLE,
                location.getBlockX(), location.getBlockY(), location.getBlockZ());

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
                Component.text(title).color(NamedTextColor.DARK_PURPLE));

        // Row 0: Current playing and music selection button
        MusicFileLoader.MusicFile currentDisc = playlistManager.getCurrentDisc(jukebox);
        if (currentDisc != null) {
            ItemStack discItem = discCreator.createMusicDisc(currentDisc, false, player);
            ItemMeta meta = discItem.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(languageService.t(player, Message.MUSIC_CURRENT_PLAYING))
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                discItem.setItemMeta(meta);
            }
            inv.setItem(CURRENT_PLAYING_SLOT, discItem);
        } else {
            inv.setItem(CURRENT_PLAYING_SLOT, createInfoItem(player, Material.BARRIER,
                    Message.MUSIC_CURRENT_PLAYING, Message.MUSIC_NO_DISC));
        }

        // Music selection button (slot 8) - only in sequential mode
        if (!data.isRandomMode) {
            inv.setItem(8, createMusicSelectionButton(player));
        }

        // Rows 1-3: Playlist area (only in sequential mode)
        if (!data.isRandomMode) {
            for (int i = 0; i < Math.min(data.playlist.size(), PLAYLIST_SLOTS.length); i++) {
                MusicFileLoader.MusicFile music = data.playlist.get(i);
                ItemStack discItem = createPlaylistDiscItem(player, music, i + 1);
                inv.setItem(PLAYLIST_SLOTS[i], discItem);
            }
        } else {
            // Fill with disabled items in random mode
            ItemStack disabledItem = createDisabledPlaylistItem(player);
            for (int slot : PLAYLIST_SLOTS) {
                inv.setItem(slot, disabledItem);
            }
        }

        // Rows 4-5: Control buttons
        // Row 4: Mode, Auto-play, Play next
        inv.setItem(38, createPlayModeButton(player, data.isRandomMode));
        inv.setItem(40, createAutoPlayButton(player, data.autoPlay));
        inv.setItem(42, createPlayNextButton(player));

        // Row 5: Add all, Clear, Close
        if (!data.isRandomMode) {
            inv.setItem(46, createAddAllButton(player));
            inv.setItem(48, createClearPlaylistButton(player));
        }
        inv.setItem(53, createCloseButton(player));

        return inv;
    }

    private ItemStack createPlaylistDiscItem(Player player, MusicFileLoader.MusicFile music, int rank) {
        ItemStack discItem = discCreator.createMusicDisc(music, false, player);
        ItemMeta meta = discItem.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(Component.text(""));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_PLAYLIST_RANK, rank))
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_PLAYLIST_PLAY_REMOVE))
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_PLAYLIST_REMOVE_ONLY))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_PLAYLIST_RANK_UP_ACTION))
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_PLAYLIST_RANK_DOWN_ACTION))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            discItem.setItemMeta(meta);
        }
        return discItem;
    }

    private ItemStack createMusicSelectionButton(Player player) {
        ItemStack button = new ItemStack(Material.MUSIC_DISC_WAIT);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_SELECT_MUSIC))
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_SELECT_MUSIC_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            button.setItemMeta(meta);
        }
        return button;
    }

    private ItemStack createDisabledPlaylistItem(Player player) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_RANDOM_MODE_UNAVAILABLE))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_RANDOM_MODE_UNAVAILABLE_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPlayModeButton(Player player, boolean isRandomMode) {
        Material material = isRandomMode ? Material.PURPLE_DYE : Material.LIME_DYE;
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            String modeName = languageService.t(player, isRandomMode
                    ? Message.MUSIC_RANDOM_MODE
                    : Message.MUSIC_SEQUENTIAL_MODE);
            meta.displayName(Component.text(modeName)
                    .color(isRandomMode ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(languageService.t(player, Message.MUSIC_CURRENT_MODE, modeName))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));

            if (isRandomMode) {
                lore.add(Component.text(languageService.t(player, Message.MUSIC_RANDOM_MODE_DESC))
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(languageService.t(player, Message.MUSIC_SEQUENTIAL_MODE_DESC))
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.text(""));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_CLICK_SWITCH_MODE))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createAutoPlayButton(Player player, boolean enabled) {
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_AUTOPLAY))
                    .color(enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(languageService.t(player, Message.MUSIC_STATUS,
                            languageService.t(player, enabled ? Message.MUSIC_STATUS_ON : Message.MUSIC_STATUS_OFF)))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));
            lore.add(Component.text(languageService.t(player, Message.MUSIC_AUTOPLAY_LORE))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(languageService.t(player, Message.CLICK_SWITCH))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createPlayNextButton(Player player) {
        ItemStack button = new ItemStack(Material.ARROW);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_PLAY_NEXT))
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_PLAY_NEXT_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createAddAllButton(Player player) {
        ItemStack button = new ItemStack(Material.CHEST);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_ADD_ALL))
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_ADD_ALL_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createClearPlaylistButton(Player player) {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.MUSIC_CLEAR_PLAYLIST))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text(languageService.t(player, Message.MUSIC_CLEAR_PLAYLIST_LORE))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createCloseButton(Player player) {
        ItemStack button = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, Message.CLOSE))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));

            button.setItemMeta(meta);
        }

        return button;
    }

    private ItemStack createInfoItem(Player player, Material material, Message name, Message description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(languageService.t(player, name))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text(languageService.t(player, description))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            item.setItemMeta(meta);
        }

        return item;
    }
}
