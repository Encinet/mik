package org.encinet.mik.module.music.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.disc.MusicDiscKeys;
import org.encinet.mik.module.music.jukebox.JukeboxAutoPlayService;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.module.music.ui.JukeboxAccess;
import org.encinet.mik.module.music.ui.JukeboxControlGui;
import org.encinet.mik.module.music.ui.MusicBrowserGui;

/** Handles physical jukebox control-panel sessions on the Bukkit main thread. */
public final class JukeboxControlListener implements Listener {

    private final MusicLibrary musicLibrary;
    private final JukeboxPlaybackService playbackService;
    private final MusicBrowserGui browser;
    private final JukeboxQueueService queueService;
    private final JukeboxControlGui controlGui;
    private final JukeboxAutoPlayService autoPlayService;
    private final LanguageService languageService;

    public JukeboxControlListener(MusicLibrary musicLibrary, JukeboxPlaybackService playbackService,
                                  MusicBrowserGui browser,
                                  JukeboxQueueService queueService,
                                  JukeboxControlGui controlGui,
                                  JukeboxAutoPlayService autoPlayService,
                                  LanguageService languageService) {
        this.musicLibrary = musicLibrary;
        this.playbackService = playbackService;
        this.browser = browser;
        this.queueService = queueService;
        this.controlGui = controlGui;
        this.autoPlayService = autoPlayService;
        this.languageService = languageService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !event.getAction().isRightClick() || !event.getPlayer().isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        Material heldType = event.getPlayer().getInventory().getItemInMainHand().getType();
        if (block == null || block.getType() != Material.JUKEBOX || !heldType.isAir()) {
            return;
        }
        event.setCancelled(true);
        if (block.getState() instanceof Jukebox jukebox) {
            controlGui.openJukeboxControl(event.getPlayer(), jukebox);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !controlGui.isJukeboxControlInventory(event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);
        handleClick(event, player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!controlGui.isJukeboxControlInventory(event.getView().getTopInventory())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private void handleClick(InventoryClickEvent event, Player player) {
        Inventory inventory = event.getView().getTopInventory();
        Location location = controlGui.getJukebox(inventory);
        if (location == null) {
            return;
        }
        Block block = location.getBlock();
        if (!JukeboxAccess.canControl(player, location)
                || !(block.getState() instanceof Jukebox jukebox)) {
            player.closeInventory();
            sendControlError(player, location);
            return;
        }

        int slot = event.getRawSlot();
        int page = controlGui.getPage(inventory);
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        if (slot == 4) {
            return;
        }
        if (slot == 8) {
            if (data.randomMode()) {
                return;
            }
            player.closeInventory();
            browser.setJukeboxContext(player.getUniqueId(), location);
            browser.openMusicInventory(player);
            return;
        }
        if (slot == JukeboxControlGui.PREVIOUS_PAGE_SLOT) {
            if (data.randomMode() || page == 0) {
                return;
            }
            controlGui.openJukeboxControlPage(player, jukebox, controlGui.getPage(inventory) - 1);
            return;
        }
        if (slot == JukeboxControlGui.NEXT_PAGE_SLOT) {
            if (data.randomMode() || page >= JukeboxControlGui.pageCount(data.queueSize()) - 1) {
                return;
            }
            controlGui.openJukeboxControlPage(player, jukebox, controlGui.getPage(inventory) + 1);
            return;
        }
        if (slot == JukeboxControlGui.STOP_EJECT_SLOT) {
            if (!MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
                return;
            }
            autoPlayService.cancelScheduledTask(location);
            if (playbackService.stopAndEject(block)) {
                player.sendMessage(languageService.text(player, Message.MUSIC_STOP_EJECT_DONE,
                        NamedTextColor.YELLOW));
            }
            controlGui.openJukeboxControlPage(player, (Jukebox) block.getState(), page);
            return;
        }
        if (controlGui.isQueueSlot(slot)) {
            if (data.randomMode()) {
                return;
            }
            handleQueueClick(event, player, jukebox, inventory, slot, page);
            return;
        }

        switch (slot) {
            case 38 -> toggleMode(player, location, jukebox, page);
            case 40 -> toggleAutoPlay(player, location, jukebox, page);
            case 42 -> playNext(player, jukebox);
            case 46 -> {
                if (!data.randomMode()) {
                    addAll(player, location, jukebox, page);
                }
            }
            case 48 -> {
                if (!data.randomMode()) {
                    clearQueue(player, location, jukebox, page);
                }
            }
            case 53 -> player.closeInventory();
            default -> { }
        }
    }

    private void handleQueueClick(InventoryClickEvent event, Player player, Jukebox jukebox,
                                     Inventory inventory, int slot, int page) {
        MusicTrack track = controlGui.queueTrackAt(inventory, slot);
        if (track == null) {
            return;
        }
        JukeboxQueueService.JukeboxState data = queueService.state(jukebox.getLocation());
        int index = data.indexOf(track);
        if (index < 0) {
            return;
        }

        if (event.isShiftClick() && event.isLeftClick()) {
            if (index > 0) {
                data.moveInQueue(index, index - 1);
                player.sendMessage(languageService.text(player, Message.MUSIC_RANK_UP, NamedTextColor.GREEN));
                controlGui.openJukeboxControlPage(player, jukebox, page);
            }
        } else if (event.isShiftClick() && event.isRightClick()) {
            if (index < data.queueSize() - 1) {
                data.moveInQueue(index, index + 1);
                player.sendMessage(languageService.text(player, Message.MUSIC_RANK_DOWN, NamedTextColor.YELLOW));
                controlGui.openJukeboxControlPage(player, jukebox, page);
            }
        } else if (event.isLeftClick()) {
            player.closeInventory();
            autoPlayService.cancelScheduledTask(jukebox.getLocation());
            playbackService.playVirtualTrackOnJukebox(player, jukebox, track, () -> {
                data.removeFromQueue(track);
                player.sendMessage(musicMessage(player, Message.MUSIC_PLAYING_REMOVED_RICH,
                        NamedTextColor.GREEN, track, NamedTextColor.AQUA));
            });
        } else if (event.isRightClick()) {
            data.removeFromQueue(track);
            player.sendMessage(musicMessage(player, Message.MUSIC_REMOVED_FROM_QUEUE_RICH,
                    NamedTextColor.YELLOW, track, NamedTextColor.AQUA));
            controlGui.openJukeboxControlPage(player, jukebox, page);
        }
    }

    private void toggleMode(Player player, Location location, Jukebox jukebox, int page) {
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        data.toggleRandomMode();
        String mode = languageService.t(player, data.randomMode()
                ? Message.MUSIC_RANDOM_MODE : Message.MUSIC_SEQUENTIAL_MODE);
        String description = languageService.t(player, data.randomMode()
                ? Message.MUSIC_RANDOM_MODE_DESC : Message.MUSIC_SEQUENTIAL_MODE_DESC);
        player.sendMessage(languageService.text(player, Message.MUSIC_MODE_SWITCHED,
                NamedTextColor.GREEN, mode, description));
        controlGui.openJukeboxControlPage(player, jukebox, page);
    }

    private void toggleAutoPlay(Player player, Location location, Jukebox jukebox, int page) {
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        data.toggleAutoPlay();
        if (data.autoPlay()) {
            player.sendMessage(languageService.text(player, Message.MUSIC_AUTOPLAY_ON, NamedTextColor.GREEN));
            if (!playbackService.isPlaying(jukebox.getBlock())) {
                autoPlayService.playNextTrack(location);
            }
        } else {
            autoPlayService.cancelScheduledTask(location);
            player.sendMessage(languageService.text(player, Message.MUSIC_AUTOPLAY_OFF, NamedTextColor.GRAY));
        }
        controlGui.openJukeboxControlPage(player, jukebox, page);
    }

    private void playNext(Player player, Jukebox jukebox) {
        if (!autoPlayService.playNextTrack(jukebox.getLocation())) {
            Message message = queueService.state(jukebox.getLocation()).randomMode()
                    ? Message.MUSIC_NO_FILES : Message.MUSIC_QUEUE_EMPTY;
            player.sendMessage(languageService.text(player, message, NamedTextColor.RED));
            return;
        }
        player.closeInventory();
    }

    private void addAll(Player player, Location location, Jukebox jukebox, int page) {
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        int added = data.addAllToQueue(musicLibrary.tracks());
        player.sendMessage(languageService.text(player, Message.MUSIC_ADD_ALL_DONE,
                NamedTextColor.GREEN, added));
        controlGui.openJukeboxControlPage(player, jukebox, page);
    }

    private void clearQueue(Player player, Location location, Jukebox jukebox, int page) {
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        int count = data.queueSize();
        data.clearQueue();
        player.sendMessage(languageService.text(player, Message.MUSIC_CLEAR_QUEUE_DONE,
                NamedTextColor.YELLOW, count));
        controlGui.openJukeboxControlPage(player, jukebox, page);
    }

    private void sendControlError(Player player, Location location) {
        if (JukeboxAccess.isAvailable(location)) {
            player.sendMessage(languageService.text(player, Message.MUSIC_JUKEBOX_TOO_FAR,
                    NamedTextColor.RED, JukeboxAccess.CONTROL_DISTANCE_BLOCKS));
        } else {
            player.sendMessage(languageService.text(player, Message.MUSIC_JUKEBOX_UNAVAILABLE,
                    NamedTextColor.RED));
        }
    }

    private Component musicMessage(Player player, Message message, NamedTextColor baseColor,
                                   MusicTrack track, NamedTextColor trackColor) {
        return languageService.rich(player, message, baseColor,
                RichArg.component("music", Component.text(track.details().title(), trackColor),
                        track.details().title()));
    }
}
