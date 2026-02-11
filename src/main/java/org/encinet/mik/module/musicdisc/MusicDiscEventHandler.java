package org.encinet.mik.module.musicdisc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private Set<Location> musicChestLocations;

    public MusicDiscEventHandler(MusicFileLoader musicFileLoader, MusicDiscCreator discCreator,
                                 MusicDiscPlayer discPlayer, MusicInventoryGUI inventoryGUI,
                                 JukeboxPlaylistManager playlistManager, JukeboxControlGUI jukeboxControlGUI,
                                 JukeboxAutoPlayManager autoPlayManager) {
        this.musicFileLoader = musicFileLoader;
        this.discCreator = discCreator;
        this.discPlayer = discPlayer;
        this.inventoryGUI = inventoryGUI;
        this.playlistManager = playlistManager;
        this.jukeboxControlGUI = jukeboxControlGUI;
        this.autoPlayManager = autoPlayManager;
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

        String title = event.getView().title().toString();

        // Handle music disc GUI
        if (title.contains("Music Discs") || title.contains("搜索:")) {
            handleMusicDiscGUI(event, player, title);
            return;
        }

        // Handle jukebox control GUI
        if (title.contains("唱片机")) {
            handleJukeboxControlGUI(event, player);
        }
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
                player.sendMessage(Component.text("该歌曲已在播放列表中")
                        .color(NamedTextColor.YELLOW));
            } else {
                data.addToPlaylist(musicFile);
                player.sendMessage(Component.text("已添加到播放列表: ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text(musicFile.displayName()).color(NamedTextColor.AQUA)));
            }
            return;
        }

        // Normal behavior: give disc or play
        if (isRightClick) {
            event.setCancelled(true);
            player.closeInventory();
            discPlayer.playDisc(player, clickedItem, musicFile.displayName());
        } else {
            ItemStack cleanDisc = discCreator.createMusicDisc(musicFile, false);
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
                player.sendMessage(Component.text("已提升排名")
                        .color(NamedTextColor.GREEN));
                jukeboxControlGUI.openJukeboxControl(player, jukebox);
            }
        } else if (event.isShiftClick() && event.isRightClick()) {
            // Shift + Right click: Decrease rank (move down in list)
            if (currentIndex < data.playlist.size() - 1) {
                data.moveInPlaylist(currentIndex, currentIndex + 1);
                player.sendMessage(Component.text("已降低排名")
                        .color(NamedTextColor.YELLOW));
                jukeboxControlGUI.openJukeboxControl(player, jukebox);
            }
        } else if (event.isLeftClick()) {
            // Left click: Play and remove from playlist
            player.closeInventory();
            ItemStack disc = discCreator.createMusicDisc(musicFile, false);
            data.removeFromPlaylist(musicFile);
            discPlayer.playDiscOnJukebox(player, jukebox, disc, musicFile.displayName());

            // Schedule auto-play if enabled
            if (data.autoPlay) {
                autoPlayManager.scheduleNextTrack(jukebox.getLocation(), musicFile);
            }

            player.sendMessage(Component.text("正在播放并已从列表移除: ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(musicFile.displayName()).color(NamedTextColor.AQUA)));
        } else if (event.isRightClick()) {
            // Right click: Remove from playlist without playing
            data.removeFromPlaylist(musicFile);
            player.sendMessage(Component.text("已从播放列表移除: ")
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text(musicFile.displayName()).color(NamedTextColor.AQUA)));
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
                .append(Component.text("点击这里搜索: ").color(NamedTextColor.YELLOW))
                .append(Component.text("[/music search]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/music search "))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                Component.text("点击填充命令").color(NamedTextColor.GRAY)
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
                player.sendMessage(Component.text("没有可用的音乐文件")
                        .color(NamedTextColor.RED));
                return;
            }

            MusicFileLoader.MusicFile randomMusic = musicFileLoader.getMusicFiles()
                    .get(RANDOM.nextInt(musicFileLoader.getMusicFiles().size()));

            JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(jukeboxLoc);

            if (data.playlist.contains(randomMusic)) {
                player.sendMessage(Component.text("该歌曲已在播放列表中，重新随机...")
                        .color(NamedTextColor.YELLOW));
                // Try again with a different random song
                int attempts = 0;
                while (data.playlist.contains(randomMusic) && attempts < 10) {
                    randomMusic = musicFileLoader.getMusicFiles()
                            .get(RANDOM.nextInt(musicFileLoader.getMusicFiles().size()));
                    attempts++;
                }

                if (data.playlist.contains(randomMusic)) {
                    player.sendMessage(Component.text("所有歌曲都已在播放列表中")
                            .color(NamedTextColor.RED));
                    return;
                }
            }

            data.addToPlaylist(randomMusic);
            player.sendMessage(Component.text("已随机添加到播放列表: ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(randomMusic.displayName()).color(NamedTextColor.AQUA)));
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
        player.sendMessage(Component.text("已清除搜索").color(NamedTextColor.GREEN));
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

        String modeName = data.isRandomMode ? "随机模式" : "列表模式";
        String description = data.isRandomMode ? "从所有歌曲中随机播放" : "按顺序播放列表";

        player.sendMessage(Component.text("已切换到" + modeName + " - " + description)
                .color(NamedTextColor.GREEN));

        jukeboxControlGUI.openJukeboxControl(player, jukebox);
    }

    private void handleAutoPlayToggle(Player player, Location location, Jukebox jukebox) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);
        data.toggleAutoPlay();

        if (data.autoPlay) {
            player.sendMessage(Component.text("自动播放: 开启")
                    .color(NamedTextColor.GREEN));

            // If jukebox is empty, play first track immediately
            if (jukebox.getRecord().getType() == Material.AIR) {
                MusicFileLoader.MusicFile nextTrack = playlistManager.getNextTrack(location);
                if (nextTrack != null) {
                    ItemStack disc = discCreator.createMusicDisc(nextTrack, false);

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
            player.sendMessage(Component.text("自动播放: 关闭")
                    .color(NamedTextColor.GRAY));
        }

        jukeboxControlGUI.openJukeboxControl(player, jukebox);
    }

    private void handlePlayNext(Player player, Jukebox jukebox) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(jukebox.getLocation());
        MusicFileLoader.MusicFile nextTrack = playlistManager.getNextTrack(jukebox.getLocation());

        if (nextTrack == null) {
            player.sendMessage(Component.text("播放列表为空")
                    .color(NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        ItemStack disc = discCreator.createMusicDisc(nextTrack, false);

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

        player.sendMessage(Component.text("已添加 " + addedCount + " 首歌曲到播放列表")
                .color(NamedTextColor.GREEN));
        jukeboxControlGUI.openJukeboxControl(player, jukebox);
    }

    private void handleClearPlaylist(Player player, Location location, Jukebox jukebox) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);
        int count = data.playlist.size();
        data.clearPlaylist();

        player.sendMessage(Component.text("已清空播放列表 (" + count + " 首歌曲)")
                .color(NamedTextColor.YELLOW));
        jukeboxControlGUI.openJukeboxControl(player, jukebox);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        inventoryGUI.removePlayerData(event.getPlayer().getUniqueId());
        jukeboxControlGUI.removePlayerJukebox(event.getPlayer().getUniqueId());
    }

    public void giveRandomDisc(Player player) {
        if (musicFileLoader.getMusicFiles().isEmpty()) {
            player.sendMessage(Component.text("没有可用的音乐文件")
                    .color(NamedTextColor.RED));
            return;
        }

        MusicFileLoader.MusicFile randomMusic = musicFileLoader.getMusicFiles()
                .get(RANDOM.nextInt(musicFileLoader.getMusicFiles().size()));

        ItemStack disc = discCreator.createMusicDisc(randomMusic, false);
        var leftover = player.getInventory().addItem(disc);

        if (leftover.isEmpty()) {
            player.sendMessage(Component.text()
                    .append(Component.text("获得随机音乐唱片: ").color(NamedTextColor.GREEN))
                    .append(Component.text(randomMusic.displayName()).color(NamedTextColor.YELLOW))
                    .build());
        } else {
            player.sendMessage(Component.text("背包已满，无法获得音乐唱片")
                    .color(NamedTextColor.RED));
        }
    }

    public void playRandomDisc(Player player) {
        if (musicFileLoader.getMusicFiles().isEmpty()) {
            player.sendMessage(Component.text("没有可用的音乐文件")
                    .color(NamedTextColor.RED));
            return;
        }

        MusicFileLoader.MusicFile randomMusic = musicFileLoader.getMusicFiles()
                .get(RANDOM.nextInt(musicFileLoader.getMusicFiles().size()));

        ItemStack disc = discCreator.createMusicDisc(randomMusic);
        discPlayer.playDisc(player, disc, randomMusic.displayName());
    }
}
