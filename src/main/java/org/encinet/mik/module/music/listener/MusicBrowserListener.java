package org.encinet.mik.module.music.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackPool;
import org.encinet.mik.module.music.catalog.MusicTrackSelector;
import org.encinet.mik.module.music.command.RandomMusicActions;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.JukeboxAutoPlayService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.module.music.jukebox.NearbyJukeboxPlayback;
import org.encinet.mik.module.music.ui.JukeboxAccess;
import org.encinet.mik.module.music.ui.JukeboxControlGui;
import org.encinet.mik.module.music.ui.MusicBrowserGui;
import org.encinet.mik.module.music.ui.MusicBrowserActionHandler;

import java.util.List;
import java.util.Set;

/** Handles music-browser sessions and track selection on the Bukkit main thread. */
public final class MusicBrowserListener implements Listener, MusicBrowserActionHandler {

    private final MusicTrackPool trackPool;
    private final MusicDiscFactory discFactory;
    private final NearbyJukeboxPlayback nearbyPlayback;
    private final MusicBrowserGui browser;
    private final JukeboxQueueService queueService;
    private final JukeboxControlGui jukeboxControlGui;
    private final JukeboxPlaybackService playbackService;
    private final JukeboxAutoPlayService autoPlayService;
    private final LanguageService languageService;
    private final MusicTrackSelector trackSelector;
    private final RandomMusicActions randomActions;
    private Set<Location> musicChestLocations = Set.of();

    public MusicBrowserListener(MusicTrackPool trackPool,
                                MusicDiscFactory discFactory,
                                NearbyJukeboxPlayback nearbyPlayback, MusicBrowserGui browser,
                                JukeboxQueueService queueService,
                                JukeboxControlGui jukeboxControlGui,
                                JukeboxPlaybackService playbackService,
                                JukeboxAutoPlayService autoPlayService,
                                LanguageService languageService, MusicTrackSelector trackSelector,
                                RandomMusicActions randomActions) {
        this.trackPool = trackPool;
        this.discFactory = discFactory;
        this.nearbyPlayback = nearbyPlayback;
        this.browser = browser;
        this.queueService = queueService;
        this.jukeboxControlGui = jukeboxControlGui;
        this.playbackService = playbackService;
        this.autoPlayService = autoPlayService;
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
        browser.openMenu(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        browser.removePlayerData(event.getPlayer().getUniqueId());
    }

    @Override
    public void track(Player player, MusicTrack track, boolean rightClick) {
        if (track == null) return;

        Location jukeboxLocation = browser.getJukeboxContext(player.getUniqueId());
        if (jukeboxLocation != null) {
            if (!JukeboxAccess.canControl(player, jukeboxLocation)) {
                browser.setJukeboxContext(player.getUniqueId(), null);
                closeMenu(player);
                sendJukeboxControlError(player, jukeboxLocation);
                return;
            }
            JukeboxQueueService.JukeboxState data = queueService.state(jukeboxLocation);
            if (rightClick) {
                Block block = jukeboxLocation.getBlock();
                if (!(block.getState() instanceof Jukebox jukebox)) {
                    return;
                }
                closeMenu(player);
                autoPlayService.cancelScheduledTask(jukeboxLocation);
                playbackService.playVirtualTrackOnJukebox(player, jukebox, track, () ->
                        player.sendMessage(musicMessage(player,
                                Message.MUSIC_PLAYING_NOW_RICH,
                                NamedTextColor.GREEN, track, NamedTextColor.AQUA)));
                return;
            }
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
            closeMenu(player);
            nearbyPlayback.play(player, track);
            return;
        }
        ItemStack disc = discFactory.createPersistentDisc(track, player);
        if (!player.getInventory().addItem(disc).isEmpty()) {
            player.sendMessage(languageService.text(player, Message.MUSIC_INVENTORY_FULL,
                    NamedTextColor.RED));
        }
    }

    @Override
    public void previousPage(Player player) {
        Integer page = browser.getPlayerPage(player.getUniqueId());
        if (page != null && page > 0) {
            browser.openCurrentPage(player, page - 1);
        }
    }

    @Override
    public void nextPage(Player player) {
        Integer page = browser.getPlayerPage(player.getUniqueId());
        if (page != null) {
            browser.openCurrentPage(player, page + 1);
        }
    }

    @Override
    public void search(Player player) {
        browser.promptSearch(player);
    }

    @Override
    public void importPlaylist(Player player) {
        browser.promptPlaylistImport(player);
    }

    @Override
    public void random(Player player, boolean rightClick) {
        Location jukeboxLocation = browser.getJukeboxContext(player.getUniqueId());
        if (jukeboxLocation != null) {
            if (!JukeboxAccess.canControl(player, jukeboxLocation)) {
                browser.setJukeboxContext(player.getUniqueId(), null);
                closeMenu(player);
                sendJukeboxControlError(player, jukeboxLocation);
                return;
            }
            JukeboxQueueService.JukeboxState data = queueService.state(jukeboxLocation);
            List<MusicTrack> candidates = browser.tracksInCurrentSection(
                    player.getUniqueId(), trackPool.tracks());
            if (rightClick) {
                MusicTrack track = trackSelector.select(candidates);
                if (track == null) {
                    player.sendMessage(languageService.text(player, Message.MUSIC_NO_FILES,
                            NamedTextColor.RED));
                    return;
                }
                Block block = jukeboxLocation.getBlock();
                if (!(block.getState() instanceof Jukebox jukebox)) {
                    return;
                }
                closeMenu(player);
                autoPlayService.cancelScheduledTask(jukeboxLocation);
                playbackService.playVirtualTrackOnJukebox(player, jukebox, track, () ->
                        player.sendMessage(musicMessage(player, Message.MUSIC_PLAYING_NOW_RICH,
                                NamedTextColor.GREEN, track, NamedTextColor.AQUA)));
                return;
            }
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

        closeMenu(player);
        List<MusicTrack> candidates = browser.tracksInCurrentSection(
                player.getUniqueId(), trackPool.tracks());
        if (rightClick) {
            randomActions.playRandomDisc(player, candidates);
        } else {
            randomActions.giveRandomDisc(player, candidates);
        }
    }

    @Override
    public void back(Player player) {
        Location jukeboxLocation = browser.getJukeboxContext(player.getUniqueId());
        if (jukeboxLocation == null) {
            browser.back(player);
            return;
        }
        browser.setJukeboxContext(player.getUniqueId(), null);
        Block block = jukeboxLocation.getBlock();
        if (JukeboxAccess.canControl(player, jukeboxLocation)
                && block.getState() instanceof Jukebox jukebox) {
            if (browser.backToParent(player)) return;
            if (browser.close(player)) {
                jukeboxControlGui.openJukeboxControl(player, jukebox);
            }
        } else {
            sendJukeboxControlError(player, jukeboxLocation);
        }
    }

    @Override
    public void library(Player player) { browser.showLibrary(player, 0); }

    @Override
    public void cycleSort(Player player) { browser.cycleSort(player); }

    @Override
    public void cycleSection(Player player) { browser.cycleSection(player); }

    private void sendJukeboxControlError(Player player, Location location) {
        if (JukeboxAccess.isAvailable(location)) {
            player.sendMessage(languageService.text(player, Message.MUSIC_JUKEBOX_TOO_FAR,
                    NamedTextColor.RED, JukeboxAccess.CONTROL_DISTANCE_BLOCKS));
        } else {
            player.sendMessage(languageService.text(player, Message.MUSIC_JUKEBOX_UNAVAILABLE,
                    NamedTextColor.RED));
        }
    }

    private void closeMenu(Player player) {
        browser.close(player);
    }

    private Component musicMessage(Player player, Message message, NamedTextColor baseColor,
                                   MusicTrack track, NamedTextColor trackColor) {
        return languageService.rich(player, message, baseColor,
                RichArg.component("music", Component.text(track.details().title(), trackColor),
                        track.details().title()));
    }

}
