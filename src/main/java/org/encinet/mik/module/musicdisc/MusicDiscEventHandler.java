package org.encinet.mik.module.musicdisc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;

import java.util.Random;
import java.util.Set;

/**
 * Event handler for music disc and jukebox interactions
 */
public class MusicDiscEventHandler implements Listener {

    private static final Random RANDOM = new Random();

    private final MusicFileLoader musicFileLoader;
    private final MusicDiscCreator discCreator;
    private final MusicDiscPlayer discPlayer;
    private final MusicInventoryGUI inventoryGUI;
    private final JukeboxPlaylistManager playlistManager;
    private final JukeboxControlGUI jukeboxControlGUI;
    private final JukeboxAutoPlayManager autoPlayManager;
    private final LanguageService languageService;
    private Set<Location> musicChestLocations;

    public MusicDiscEventHandler(MusicFileLoader musicFileLoader, MusicDiscCreator discCreator,
                                 MusicDiscPlayer discPlayer, MusicInventoryGUI inventoryGUI,
                                 JukeboxPlaylistManager playlistManager, JukeboxControlGUI jukeboxControlGUI,
                                 JukeboxAutoPlayManager autoPlayManager, LanguageService languageService) {
        this.musicFileLoader = musicFileLoader;
        this.discCreator = discCreator;
        this.discPlayer = discPlayer;
        this.inventoryGUI = inventoryGUI;
        this.playlistManager = playlistManager;
        this.jukeboxControlGUI = jukeboxControlGUI;
        this.autoPlayManager = autoPlayManager;
        this.languageService = languageService;
    }

    /**
     * Set music chest locations
     */
    public void setMusicChestLocations(Set<Location> locations) {
        this.musicChestLocations = locations;
    }

    /**
     * Handle player interact event for music chests and jukeboxes
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Player player = event.getPlayer();

        // Check for music chest interaction
        if (musicChestLocations != null && !musicChestLocations.isEmpty()) {
            Location clickedLocation = clickedBlock.getLocation();
            if (musicChestLocations.contains(clickedLocation)) {
                event.setCancelled(true);
                inventoryGUI.openMusicInventory(player);
                return;
            }
        }

        // Check for jukebox control panel (empty hand + shift + right click)
        if (clickedBlock.getType() == Material.JUKEBOX && player.isSneaking()) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand.getType() == Material.AIR) {
                event.setCancelled(true);
                if (clickedBlock.getState() instanceof Jukebox jukebox) {
                    jukeboxControlGUI.openJukeboxControl(player, jukebox);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        // Handle music disc GUI
        if (isMusicMenuTitle(title)) {
            handleMusicDiscGUI(event, player, title);
            return;
        }

        // Handle jukebox control GUI
        if (isJukeboxTitle(title)) {
            handleJukeboxControlGUI(event, player);
        }
    }

    private boolean isMusicMenuTitle(String title) {
        for (org.encinet.mik.module.i18n.Language language : org.encinet.mik.module.i18n.Language.values()) {
            if (title.startsWith(staticTitlePrefix(languageService.t(language, Message.MUSIC_MENU_TITLE, 1, 1), "1"))
                    || title.startsWith(staticTitlePrefix(languageService.t(language, Message.MUSIC_MENU_SEARCH_TITLE, "MIK_SEARCH", 1, 1), "MIK_SEARCH"))) {
                return true;
            }
        }
        return false;
    }

    private boolean isJukeboxTitle(String title) {
        for (org.encinet.mik.module.i18n.Language language : org.encinet.mik.module.i18n.Language.values()) {
            if (title.startsWith(staticTitlePrefix(languageService.t(language, Message.MUSIC_JUKEBOX_TITLE, 0, 0, 0), "0"))) {
                return true;
            }
        }
        return false;
    }

    private String staticTitlePrefix(String sample, String marker) {
        int index = sample.indexOf(marker);
        return index < 0 ? sample : sample.substring(0, index);
    }

    private void handleMusicDiscGUI(InventoryClickEvent event, Player player, String title) {
        int slot = event.getRawSlot();
        boolean isRightClick = event.isRightClick();

        if (slot >= 0 && slot < 45) {
            handleMusicDiscClick(event, player, isRightClick);
            return;
        }

        if (slot == 45) {
            handlePreviousPage(event, player);
        } else if (slot == 53) {
            handleNextPage(event, player);
        } else if (slot == 47) {
            handleSearchButton(event, player);
        } else if (slot == 46) {
            handleRandomDiscButton(event, player, isRightClick);
        } else if (slot == 51) {
            event.setCancelled(true);
        } else if (slot == 48) {
            handleClearSearchButton(event, player);
        } else if (slot == 49) {
            event.setCancelled(true);
        } else if (slot == 52) {
            handleBackButton(event, player);
        } else if (slot >= 45 && slot < 54) {
            event.setCancelled(true);
        }
    }

    private void handleJukeboxControlGUI(InventoryClickEvent event, Player player) {
        Location jukeboxLoc = jukeboxControlGUI.getPlayerJukebox(player.getUniqueId());
        if (jukeboxLoc == null) return;

        Block block = jukeboxLoc.getBlock();
        if (!(block.getState() instanceof Jukebox jukebox)) return;

        int slot = event.getRawSlot();
        event.setCancelled(true);

        // Current playing disc (slot 4) - no action
        if (slot == 4) return;

        // Music selection button (slot 8)
        if (slot == 8) {
            player.closeInventory();
            inventoryGUI.setJukeboxContext(player.getUniqueId(), jukeboxLoc);
            inventoryGUI.openMusicInventory(player);
            return;
        }

        // Playlist items (slots 9-35)
        if (jukeboxControlGUI.isPlaylistSlot(slot)) {
            handlePlaylistClick(event, player, jukebox, slot);
            return;
        }

        // Control buttons
        switch (slot) {
            case 38 -> handlePlayModeToggle(player, jukeboxLoc, jukebox);
            case 40 -> handleAutoPlayToggle(player, jukeboxLoc, jukebox);
            case 42 -> handlePlayNext(player, jukebox);
            case 46 -> handleAddAll(player, jukeboxLoc, jukebox);
            case 48 -> handleClearPlaylist(player, jukeboxLoc, jukebox);
            case 50 -> {} // Info button - no action
            case 53 -> player.closeInventory();
        }
    }

    private void handleMusicDiscClick(InventoryClickEvent event, Player player, boolean isRightClick) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.getType().toString().startsWith("MUSIC_DISC_")) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey identifierKey = new NamespacedKey("pv-addon-discs", "identifier");
        String identifier = pdc.get(identifierKey, PersistentDataType.STRING);

        if (identifier == null || !identifier.startsWith("local://")) return;

        String fileName = identifier.substring(8);
        MusicFileLoader.MusicFile musicFile = musicFileLoader.getMusicFiles().stream()
                .filter(m -> m.fileName().equals(fileName))
                .findFirst()
                .orElse(null);

        if (musicFile == null) return;

        // Check if player is in jukebox context
        Location jukeboxLoc = inventoryGUI.getJukeboxContext(player.getUniqueId());
        if (jukeboxLoc != null) {
            // Add to jukebox playlist
            event.setCancelled(true);
            JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(jukeboxLoc);

            if (data.playlist.contains(musicFile)) {
                player.sendMessage(languageService.text(player, Message.MUSIC_DUPLICATE_IN_PLAYLIST,
                        NamedTextColor.YELLOW));
            } else {
                data.addToPlaylist(musicFile);
                player.sendMessage(musicMessage(player, Message.MUSIC_ADDED_TO_PLAYLIST_RICH,
                        NamedTextColor.GREEN, musicFile, NamedTextColor.AQUA));
            }
            return;
        }

        // Normal behavior: give disc or play
        if (isRightClick) {
            event.setCancelled(true);
            player.closeInventory();
            discPlayer.playDisc(player, clickedItem, musicFile.displayName());
        } else {
            ItemStack cleanDisc = discCreator.createMusicDisc(musicFile, false, player);
            event.setCurrentItem(cleanDisc);
        }
    }

    private void handlePlaylistClick(InventoryClickEvent event, Player player, Jukebox jukebox, int slot) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.getType().toString().startsWith("MUSIC_DISC_")) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey identifierKey = new NamespacedKey("pv-addon-discs", "identifier");
        String identifier = pdc.get(identifierKey, PersistentDataType.STRING);

        if (identifier == null || !identifier.startsWith("local://")) return;

        String fileName = identifier.substring(8);
        MusicFileLoader.MusicFile musicFile = musicFileLoader.getMusicFiles().stream()
                .filter(m -> m.fileName().equals(fileName))
                .findFirst()
                .orElse(null);

        if (musicFile == null) return;

        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(jukebox.getLocation());
        int currentIndex = data.playlist.indexOf(musicFile);
        if (currentIndex == -1) return;

        if (event.isShiftClick() && event.isLeftClick()) {
            // Shift + Left click: Increase rank (move up in list)
            if (currentIndex > 0) {
                data.moveInPlaylist(currentIndex, currentIndex - 1);
                player.sendMessage(languageService.text(player, Message.MUSIC_RANK_UP, NamedTextColor.GREEN));
                jukeboxControlGUI.openJukeboxControl(player, jukebox);
            }
        } else if (event.isShiftClick() && event.isRightClick()) {
            // Shift + Right click: Decrease rank (move down in list)
            if (currentIndex < data.playlist.size() - 1) {
                data.moveInPlaylist(currentIndex, currentIndex + 1);
                player.sendMessage(languageService.text(player, Message.MUSIC_RANK_DOWN, NamedTextColor.YELLOW));
                jukeboxControlGUI.openJukeboxControl(player, jukebox);
            }
        } else if (event.isLeftClick()) {
            // Left click: Play and remove from playlist
            player.closeInventory();
            ItemStack disc = discCreator.createMusicDisc(musicFile, false, player);
            data.removeFromPlaylist(musicFile);
            discPlayer.playDiscOnJukebox(player, jukebox, disc, musicFile.displayName());

            // Schedule auto-play if enabled
            if (data.autoPlay) {
                autoPlayManager.scheduleNextTrack(jukebox.getLocation(), musicFile);
            }

            player.sendMessage(musicMessage(player, Message.MUSIC_PLAYING_REMOVED_RICH,
                    NamedTextColor.GREEN, musicFile, NamedTextColor.AQUA));
        } else if (event.isRightClick()) {
            // Right click: Remove from playlist without playing
            data.removeFromPlaylist(musicFile);
            player.sendMessage(musicMessage(player, Message.MUSIC_REMOVED_FROM_PLAYLIST_RICH,
                    NamedTextColor.YELLOW, musicFile, NamedTextColor.AQUA));
            jukeboxControlGUI.openJukeboxControl(player, jukebox);
        }
    }

    private void handlePreviousPage(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Integer currentPage = inventoryGUI.getPlayerPage(player.getUniqueId());
        if (currentPage != null && currentPage > 0) {
            String keyword = inventoryGUI.getPlayerSearchKeyword(player.getUniqueId());
            inventoryGUI.openMusicInventory(player, keyword, currentPage - 1);
        }
    }

    private void handleNextPage(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Integer currentPage = inventoryGUI.getPlayerPage(player.getUniqueId());
        if (currentPage != null) {
            String keyword = inventoryGUI.getPlayerSearchKeyword(player.getUniqueId());
            inventoryGUI.openMusicInventory(player, keyword, currentPage + 1);
        }
    }

    private void handleSearchButton(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        player.closeInventory();
        player.sendMessage(Component.text()
                .append(Component.text(languageService.t(player, Message.MUSIC_SEARCH_PROMPT), NamedTextColor.YELLOW))
                .append(Component.space())
                .append(Component.text(languageService.t(player, Message.MUSIC_SEARCH_PROMPT_COMMAND))
                        .color(NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/music search "))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                Component.text(languageService.t(player, Message.MUSIC_SEARCH_PROMPT_HOVER),
                                        NamedTextColor.GRAY)
                        )))
                .build());
    }

    private void handleRandomDiscButton(InventoryClickEvent event, Player player, boolean isRightClick) {
        event.setCancelled(true);

        // Check if player is in jukebox context
        Location jukeboxLoc = inventoryGUI.getJukeboxContext(player.getUniqueId());
        if (jukeboxLoc != null) {
            // Add random song to playlist
            if (musicFileLoader.getMusicFiles().isEmpty()) {
                player.sendMessage(languageService.text(player, Message.MUSIC_NO_FILES, NamedTextColor.RED));
                return;
            }

            MusicFileLoader.MusicFile randomMusic = musicFileLoader.getMusicFiles()
                    .get(RANDOM.nextInt(musicFileLoader.getMusicFiles().size()));

            JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(jukeboxLoc);

            if (data.playlist.contains(randomMusic)) {
                player.sendMessage(languageService.text(player, Message.MUSIC_DUPLICATE_REROLL,
                        NamedTextColor.YELLOW));
                // Try again with a different random song
                int attempts = 0;
                while (data.playlist.contains(randomMusic) && attempts < 10) {
                    randomMusic = musicFileLoader.getMusicFiles()
                            .get(RANDOM.nextInt(musicFileLoader.getMusicFiles().size()));
                    attempts++;
                }

                if (data.playlist.contains(randomMusic)) {
                    player.sendMessage(languageService.text(player, Message.MUSIC_ALL_IN_PLAYLIST,
                            NamedTextColor.RED));
                    return;
                }
            }

            data.addToPlaylist(randomMusic);
            player.sendMessage(musicMessage(player, Message.MUSIC_RANDOM_ADDED_TO_PLAYLIST_RICH,
                    NamedTextColor.GREEN, randomMusic, NamedTextColor.AQUA));
            return;
        }

        // Normal behavior: give disc or play
        player.closeInventory();
        if (isRightClick) {
            playRandomDisc(player);
        } else {
            giveRandomDisc(player);
        }
    }

    private void handleClearSearchButton(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        inventoryGUI.removePlayerData(player.getUniqueId());
        inventoryGUI.openMusicInventory(player, null, 0);
        player.sendMessage(languageService.text(player, Message.MUSIC_SEARCH_CLEARED, NamedTextColor.GREEN));
    }

    private void handleBackButton(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Location jukeboxLoc = inventoryGUI.getJukeboxContext(player.getUniqueId());
        if (jukeboxLoc != null) {
            Block block = jukeboxLoc.getBlock();
            if (block.getState() instanceof Jukebox jukebox) {
                player.closeInventory();
                jukeboxControlGUI.openJukeboxControl(player, jukebox);
            }
        }
    }

    private void handlePlayModeToggle(Player player, Location location, Jukebox jukebox) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);
        data.toggleRandomMode();

        String modeName = languageService.t(player, data.isRandomMode
                ? Message.MUSIC_RANDOM_MODE
                : Message.MUSIC_SEQUENTIAL_MODE);
        String description = languageService.t(player, data.isRandomMode
                ? Message.MUSIC_RANDOM_MODE_DESC
                : Message.MUSIC_SEQUENTIAL_MODE_DESC);

        player.sendMessage(languageService.text(player, Message.MUSIC_MODE_SWITCHED,
                NamedTextColor.GREEN, modeName, description));

        jukeboxControlGUI.openJukeboxControl(player, jukebox);
    }

    private void handleAutoPlayToggle(Player player, Location location, Jukebox jukebox) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);
        data.toggleAutoPlay();

        if (data.autoPlay) {
            player.sendMessage(languageService.text(player, Message.MUSIC_AUTOPLAY_ON, NamedTextColor.GREEN));

            // If jukebox is empty, play first track immediately
            if (jukebox.getRecord().getType() == Material.AIR) {
                MusicFileLoader.MusicFile nextTrack = playlistManager.getNextTrack(location);
                if (nextTrack != null) {
                    ItemStack disc = discCreator.createMusicDisc(nextTrack, false, player);

                    // Remove from playlist if in sequential mode
                    if (!data.isRandomMode) {
                        data.removeFromPlaylist(nextTrack);
                    }

                    discPlayer.playDiscOnJukebox(player, jukebox, disc, nextTrack.displayName());

                    // Schedule next track
                    autoPlayManager.scheduleNextTrack(location, nextTrack);
                }
            }
        } else {
            autoPlayManager.cancelScheduledTask(location);
            player.sendMessage(languageService.text(player, Message.MUSIC_AUTOPLAY_OFF, NamedTextColor.GRAY));
        }

        jukeboxControlGUI.openJukeboxControl(player, jukebox);
    }

    private void handlePlayNext(Player player, Jukebox jukebox) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(jukebox.getLocation());
        MusicFileLoader.MusicFile nextTrack = playlistManager.getNextTrack(jukebox.getLocation());

        if (nextTrack == null) {
            player.sendMessage(languageService.text(player, Message.MUSIC_PLAYLIST_EMPTY, NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        ItemStack disc = discCreator.createMusicDisc(nextTrack, false, player);

        // Remove from playlist if in sequential mode
        if (!data.isRandomMode) {
            data.removeFromPlaylist(nextTrack);
        }

        discPlayer.playDiscOnJukebox(player, jukebox, disc, nextTrack.displayName());

        // Schedule auto-play if enabled
        if (data.autoPlay) {
            autoPlayManager.scheduleNextTrack(jukebox.getLocation(), nextTrack);
        }
    }

    private void handleAddAll(Player player, Location location, Jukebox jukebox) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);

        int addedCount = 0;
        for (MusicFileLoader.MusicFile music : musicFileLoader.getMusicFiles()) {
            if (!data.playlist.contains(music)) {
                data.addToPlaylist(music);
                addedCount++;
            }
        }

        player.sendMessage(languageService.text(player, Message.MUSIC_ADD_ALL_DONE,
                NamedTextColor.GREEN, addedCount));
        jukeboxControlGUI.openJukeboxControl(player, jukebox);
    }

    private void handleClearPlaylist(Player player, Location location, Jukebox jukebox) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);
        int count = data.playlist.size();
        data.clearPlaylist();

        player.sendMessage(languageService.text(player, Message.MUSIC_CLEAR_PLAYLIST_DONE,
                NamedTextColor.YELLOW, count));
        jukeboxControlGUI.openJukeboxControl(player, jukebox);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        inventoryGUI.removePlayerData(event.getPlayer().getUniqueId());
        jukeboxControlGUI.removePlayerJukebox(event.getPlayer().getUniqueId());
    }

    public void giveRandomDisc(Player player) {
        if (musicFileLoader.getMusicFiles().isEmpty()) {
            player.sendMessage(languageService.text(player, Message.MUSIC_NO_FILES, NamedTextColor.RED));
            return;
        }

        MusicFileLoader.MusicFile randomMusic = musicFileLoader.getMusicFiles()
                .get(RANDOM.nextInt(musicFileLoader.getMusicFiles().size()));

        ItemStack disc = discCreator.createMusicDisc(randomMusic, false, player);
        var leftover = player.getInventory().addItem(disc);

        if (leftover.isEmpty()) {
            player.sendMessage(musicMessage(player, Message.MUSIC_RANDOM_DISC_GOT_RICH,
                    NamedTextColor.GREEN, randomMusic, NamedTextColor.YELLOW));
        } else {
            player.sendMessage(languageService.text(player, Message.MUSIC_INVENTORY_FULL, NamedTextColor.RED));
        }
    }

    public void playRandomDisc(Player player) {
        if (musicFileLoader.getMusicFiles().isEmpty()) {
            player.sendMessage(languageService.text(player, Message.MUSIC_NO_FILES, NamedTextColor.RED));
            return;
        }

        MusicFileLoader.MusicFile randomMusic = musicFileLoader.getMusicFiles()
                .get(RANDOM.nextInt(musicFileLoader.getMusicFiles().size()));

        ItemStack disc = discCreator.createMusicDisc(randomMusic, player);
        discPlayer.playDisc(player, disc, randomMusic.displayName());
    }

    private Component musicMessage(Player player, Message message, NamedTextColor baseColor,
                                   MusicFileLoader.MusicFile music, NamedTextColor musicColor) {
        return languageService.rich(player, message, baseColor,
                RichArg.component("music", Component.text(music.displayName(), musicColor), music.displayName()));
    }
}
