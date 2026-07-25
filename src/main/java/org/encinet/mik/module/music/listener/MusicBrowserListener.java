package org.encinet.mik.module.music.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackPool;
import org.encinet.mik.module.music.catalog.MusicTrackSelector;
import org.encinet.mik.module.music.command.RandomMusicActions;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.NearbyJukeboxPlayback;
import org.encinet.mik.module.music.ui.JukeboxAccess;
import org.encinet.mik.module.music.ui.JukeboxControlGui;
import org.encinet.mik.module.music.ui.MusicBrowserGui;

import java.util.List;
import java.util.Set;

/** Handles music-browser sessions and track selection on the Bukkit main thread. */
public final class MusicBrowserListener implements Listener {

    private final MusicLibrary musicLibrary;
    private final MusicTrackPool trackPool;
    private final MusicDiscFactory discFactory;
    private final NearbyJukeboxPlayback nearbyPlayback;
    private final MusicBrowserGui browser;
    private final JukeboxQueueService queueService;
    private final JukeboxControlGui jukeboxControlGui;
    private final LanguageService languageService;
    private final MusicTrackSelector trackSelector;
    private final RandomMusicActions randomActions;
    private Set<Location> musicChestLocations = Set.of();

    public MusicBrowserListener(MusicLibrary musicLibrary, MusicTrackPool trackPool,
                                MusicDiscFactory discFactory,
                                NearbyJukeboxPlayback nearbyPlayback, MusicBrowserGui browser,
                                JukeboxQueueService queueService,
                                JukeboxControlGui jukeboxControlGui,
                                LanguageService languageService, MusicTrackSelector trackSelector,
                                RandomMusicActions randomActions) {
        this.musicLibrary = musicLibrary;
        this.trackPool = trackPool;
        this.discFactory = discFactory;
        this.nearbyPlayback = nearbyPlayback;
        this.browser = browser;
        this.queueService = queueService;
        this.jukeboxControlGui = jukeboxControlGui;
        this.languageService = languageService;
        this.trackSelector = trackSelector;
        this.randomActions = randomActions;
    }

    public void setMusicChestLocations(Set<Location> locations) {
        musicChestLocations = locations == null ? Set.of() : Set.copyOf(locations);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !event.getAction().isRightClick() || musicChestLocations.isEmpty()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !musicChestLocations.contains(block.getLocation())) {
            return;
        }
        event.setCancelled(true);
        browser.setJukeboxContext(event.getPlayer().getUniqueId(), null);
        browser.openMusicInventory(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !browser.isMusicInventory(event.getView().getTopInventory())) {
            return;
        }
        if (!browser.isCurrentInventory(player.getUniqueId(),
                event.getView().getTopInventory())) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() >= topSize) {
            if (event.isShiftClick()
                    || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }
        handleClick(event, player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!browser.isMusicInventory(event.getView().getTopInventory())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        browser.removePlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !browser.isMusicInventory(event.getView().getTopInventory())) {
            return;
        }
        browser.closeBrowserInventory(player.getUniqueId(), event.getView().getTopInventory());
    }

    private void handleClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        boolean rightClick = event.isRightClick();
        if (slot >= 0 && slot < 45) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.getType().toString().startsWith("MUSIC_DISC_")) {
                event.setCancelled(true);
                return;
            }
            handleTrackClick(event, player, rightClick);
            return;
        }

        switch (slot) {
            case 45 -> previousPage(event, player);
            case 46 -> {
                event.setCancelled(true);
                browser.showLibrary(player, 0);
            }
            case 47 -> search(event, player);
            case 48 -> {
                event.setCancelled(true);
                browser.cycleSort(player);
            }
            case 50 -> randomTrack(event, player, rightClick);
            case 52 -> back(event, player);
            case 53 -> nextPage(event, player);
            default -> {
                if (slot >= 45 && slot < 54) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private void handleTrackClick(InventoryClickEvent event, Player player, boolean rightClick) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        MusicTrack track = browser.trackAt(player.getUniqueId(),
                event.getView().getTopInventory(), event.getRawSlot(), clickedItem);
        if (track == null) {
            return;
        }

        Location jukeboxLocation = browser.getJukeboxContext(player.getUniqueId());
        if (jukeboxLocation != null) {
            if (!JukeboxAccess.canControl(player, jukeboxLocation)) {
                browser.setJukeboxContext(player.getUniqueId(), null);
                player.closeInventory();
                sendJukeboxControlError(player, jukeboxLocation);
                return;
            }
            JukeboxQueueService.JukeboxState data = queueService.state(jukeboxLocation);
            if (data.contains(track)) {
                player.sendMessage(languageService.text(player, Message.MUSIC_DUPLICATE_IN_QUEUE,
                        NamedTextColor.YELLOW));
            } else {
                data.addToQueue(track);
                player.sendMessage(musicMessage(player, Message.MUSIC_ADDED_TO_QUEUE_RICH,
                        NamedTextColor.GREEN, track, NamedTextColor.AQUA));
            }
            return;
        }

        if (rightClick) {
            player.closeInventory();
            nearbyPlayback.play(player, track);
            return;
        }
        ItemStack disc = discFactory.createPersistentDisc(track, player);
        if (!player.getInventory().addItem(disc).isEmpty()) {
            player.sendMessage(languageService.text(player, Message.MUSIC_INVENTORY_FULL,
                    NamedTextColor.RED));
        }
    }

    private void previousPage(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Integer page = browser.getPlayerPage(player.getUniqueId());
        if (page != null && page > 0) {
            browser.openCurrentPage(player, page - 1);
        }
    }

    private void nextPage(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Integer page = browser.getPlayerPage(player.getUniqueId());
        if (page != null) {
            browser.openCurrentPage(player, page + 1);
        }
    }

    private void search(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        browser.prepareJukeboxSearch(player.getUniqueId());
        player.closeInventory();
        String command = "/music search ";
        player.sendMessage(Component.text()
                .append(Component.text(languageService.t(player, Message.MUSIC_SEARCH_PROMPT),
                        NamedTextColor.YELLOW))
                .append(Component.space())
                .append(Component.text("[" + command.strip() + "]", NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(command))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                Component.text(languageService.t(player, Message.MUSIC_SEARCH_PROMPT_HOVER),
                                        NamedTextColor.GRAY))))
                .build());
    }

    private void randomTrack(InventoryClickEvent event, Player player, boolean rightClick) {
        event.setCancelled(true);
        Location jukeboxLocation = browser.getJukeboxContext(player.getUniqueId());
        if (jukeboxLocation != null) {
            if (!JukeboxAccess.canControl(player, jukeboxLocation)) {
                browser.setJukeboxContext(player.getUniqueId(), null);
                player.closeInventory();
                sendJukeboxControlError(player, jukeboxLocation);
                return;
            }
            JukeboxQueueService.JukeboxState data = queueService.state(jukeboxLocation);
            List<MusicTrack> candidates = trackPool.tracks();
            MusicTrack track = trackSelector.select(candidates,
                    candidate -> !data.contains(candidate));
            if (track == null) {
                Message message = candidates.isEmpty()
                        ? Message.MUSIC_NO_FILES : Message.MUSIC_ALL_IN_QUEUE;
                player.sendMessage(languageService.text(player, message, NamedTextColor.RED));
                return;
            }
            data.addToQueue(track);
            player.sendMessage(musicMessage(player, Message.MUSIC_RANDOM_ADDED_TO_QUEUE_RICH,
                    NamedTextColor.GREEN, track, NamedTextColor.AQUA));
            return;
        }

        player.closeInventory();
        if (rightClick) {
            randomActions.playRandomDisc(player);
        } else {
            randomActions.giveRandomDisc(player);
        }
    }

    private void back(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Location jukeboxLocation = browser.getJukeboxContext(player.getUniqueId());
        if (jukeboxLocation == null) {
            return;
        }
        browser.setJukeboxContext(player.getUniqueId(), null);
        Block block = jukeboxLocation.getBlock();
        if (JukeboxAccess.canControl(player, jukeboxLocation)
                && block.getState() instanceof Jukebox jukebox) {
            player.closeInventory();
            jukeboxControlGui.openJukeboxControl(player, jukebox);
        } else {
            sendJukeboxControlError(player, jukeboxLocation);
        }
    }

    private void sendJukeboxControlError(Player player, Location location) {
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
