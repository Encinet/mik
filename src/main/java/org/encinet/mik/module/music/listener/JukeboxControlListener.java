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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.jukebox.JukeboxAutoPlayService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackMode;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.module.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.module.music.jukebox.JukeboxSoundSettings;
import org.encinet.mik.module.music.ui.JukeboxAccess;
import org.encinet.mik.module.music.ui.JukeboxControlActionHandler;
import org.encinet.mik.module.music.ui.JukeboxControlGui;
import org.encinet.mik.module.music.ui.MusicBrowserGui;
import org.encinet.mik.module.music.rhythm.RhythmGameService;

/** Handles physical jukebox control-panel sessions on the Bukkit main thread. */
public final class JukeboxControlListener implements Listener, JukeboxControlActionHandler {

    private final MusicLibrary musicLibrary;
    private final JukeboxPlaybackService playbackService;
    private final MusicBrowserGui browser;
    private final JukeboxQueueService queueService;
    private final JukeboxControlGui controlGui;
    private final JukeboxAutoPlayService autoPlayService;
    private final JukeboxSettingsStore settingsStore;
    private final LanguageService languageService;
    private final RhythmGameService rhythmGameService;

    public JukeboxControlListener(MusicLibrary musicLibrary, JukeboxPlaybackService playbackService,
                                  MusicBrowserGui browser,
                                  JukeboxQueueService queueService,
                                  JukeboxControlGui controlGui,
                                  JukeboxAutoPlayService autoPlayService,
                                  JukeboxSettingsStore settingsStore,
                                  LanguageService languageService,
                                  RhythmGameService rhythmGameService) {
        this.musicLibrary = musicLibrary;
        this.playbackService = playbackService;
        this.browser = browser;
        this.queueService = queueService;
        this.controlGui = controlGui;
        this.autoPlayService = autoPlayService;
        this.settingsStore = settingsStore;
        this.languageService = languageService;
        this.rhythmGameService = rhythmGameService;
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
            controlGui.openOrRepositionJukeboxControl(event.getPlayer(), jukebox);
        }
    }

    @Override
    public void selectMusic(Player player, Location location) {
        if (resolveJukebox(player, location) == null) return;
        browser.setJukeboxContext(player.getUniqueId(), location);
        browser.openMenu(player);
    }

    @Override
    public void openPage(Player player, Location location, int page) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox != null) controlGui.openJukeboxControlPage(player, jukebox, page);
    }

    @Override
    public void queueTrack(Player player, Location location, MusicTrack track,
                           FloatingMenuInteraction interaction) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox == null || track == null) return;
        if (interaction.scroll()) {
            handleQueueScroll(player, jukebox, track,
                    interaction == FloatingMenuInteraction.SCROLL_DOWN);
        } else {
            handleQueueClick(player, jukebox, track,
                    interaction == FloatingMenuInteraction.SECONDARY);
        }
    }

    @Override
    public void stopAndEject(Player player, Location location) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox == null || !jukebox.hasRecord()) return;
        autoPlayService.cancelScheduledTask(location);
        if (playbackService.stopAndEject(jukebox.getBlock())) {
            player.sendMessage(languageService.text(player, Message.MUSIC_STOP_EJECT_DONE,
                    NamedTextColor.YELLOW));
        }
    }

    @Override
    public void adjustVolume(Player player, Location location,
                             FloatingMenuInteraction interaction) {
        adjustSetting(player, location, true, interaction);
    }

    @Override
    public void adjustRange(Player player, Location location,
                            FloatingMenuInteraction interaction) {
        adjustSetting(player, location, false, interaction);
    }

    @Override
    public void cycleMode(Player player, Location location) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox != null) cyclePlaybackMode(player, location);
    }

    @Override
    public void playNext(Player player, Location location) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox != null) playNextTrack(player, jukebox);
    }

    @Override
    public void addAll(Player player, Location location) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox != null) addAllTracks(player, location);
    }

    @Override
    public void clearQueue(Player player, Location location) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox != null) clearQueuedTracks(player, location);
    }

    @Override
    public void openRhythmGame(Player player, Location location) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox != null) rhythmGameService.open(player, jukebox.getLocation());
    }

    @Override
    public void close(Player player) {
        closeMenu(player);
    }

    private void handleQueueScroll(Player player, Jukebox jukebox, MusicTrack track,
                                   boolean down) {
        JukeboxQueueService.JukeboxState data = queueService.state(jukebox.getLocation());
        int index = data.indexOf(track);
        int destination = down ? index + 1 : index - 1;
        if (index < 0 || destination < 0 || destination >= data.queueSize()) return;
        data.moveInQueue(index, destination);
        player.sendMessage(languageService.text(player, down ? Message.MUSIC_RANK_DOWN : Message.MUSIC_RANK_UP,
                down ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
    }

    private void adjustSetting(Player player, Location location, boolean volume,
                               FloatingMenuInteraction interaction) {
        Jukebox jukebox = resolveJukebox(player, location);
        if (jukebox == null) return;
        boolean fine = interaction.scroll();
        boolean increase = interaction == FloatingMenuInteraction.PRIMARY
                || interaction == FloatingMenuInteraction.SCROLL_UP;
        int step = volume
                ? (fine ? JukeboxSoundSettings.VOLUME_FINE_STEP
                        : JukeboxSoundSettings.VOLUME_COARSE_STEP)
                : (fine ? JukeboxSoundSettings.RANGE_FINE_STEP
                        : JukeboxSoundSettings.RANGE_COARSE_STEP);
        int delta = increase ? step : -step;
        JukeboxSoundSettings current = settingsStore.read(jukebox);
        JukeboxSoundSettings updated = volume
                ? current.withVolumeDelta(delta) : current.withRangeDelta(delta);
        if (!updated.equals(current)) {
            settingsStore.write(jukebox, updated);
            playbackService.updateSettings(jukebox.getBlock(), updated);
        }
    }

    private void handleQueueClick(Player player, Jukebox jukebox,
                                  MusicTrack track, boolean alternate) {
        JukeboxQueueService.JukeboxState data = queueService.state(jukebox.getLocation());
        int index = data.indexOf(track);
        if (index < 0) {
            return;
        }

        if (!alternate) {
            closeMenu(player);
            autoPlayService.cancelScheduledTask(jukebox.getLocation());
            playbackService.playVirtualTrackOnJukebox(player, jukebox, track, () ->
                    player.sendMessage(musicMessage(player, Message.MUSIC_PLAYING_NOW_RICH,
                            NamedTextColor.GREEN, track, NamedTextColor.AQUA)));
        } else {
            data.removeFromQueue(track);
            player.sendMessage(musicMessage(player, Message.MUSIC_REMOVED_FROM_QUEUE_RICH,
                    NamedTextColor.YELLOW, track, NamedTextColor.AQUA));
        }
    }

    private void cyclePlaybackMode(Player player, Location location) {
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        JukeboxPlaybackMode mode = data.cyclePlaybackMode();
        String modeName = languageService.t(player, modeName(mode));
        String description = languageService.t(player, modeDescription(mode));
        player.sendMessage(languageService.text(player, Message.MUSIC_MODE_SWITCHED,
                NamedTextColor.GREEN, modeName, description));
    }

    private void playNextTrack(Player player, Jukebox jukebox) {
        if (!autoPlayService.playNextTrack(jukebox.getLocation())) {
            player.sendMessage(languageService.text(player, Message.MUSIC_QUEUE_EMPTY,
                    NamedTextColor.RED));
            return;
        }
        closeMenu(player);
    }

    private void addAllTracks(Player player, Location location) {
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        int added = data.addAllToQueue(musicLibrary.tracks());
        player.sendMessage(languageService.text(player, Message.MUSIC_ADD_ALL_DONE,
                NamedTextColor.GREEN, added));
    }

    private void clearQueuedTracks(Player player, Location location) {
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        int count = data.queueSize();
        data.clearQueue();
        player.sendMessage(languageService.text(player, Message.MUSIC_CLEAR_QUEUE_DONE,
                NamedTextColor.YELLOW, count));
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

    private Jukebox resolveJukebox(Player player, Location location) {
        if (location != null && JukeboxAccess.canControl(player, location)) {
            Block block = location.getBlock();
            if (block.getState() instanceof Jukebox jukebox) return jukebox;
        }
        closeMenu(player);
        sendControlError(player, location);
        return null;
    }

    private static void closeMenu(Player player) {
        FloatingMenus.current(player).ifPresent(handle -> handle.close());
    }

    private static Message modeName(JukeboxPlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ALL -> Message.MUSIC_SEQUENTIAL_MODE;
            case REPEAT_ONE -> Message.MUSIC_REPEAT_ONE_MODE;
            case SHUFFLE -> Message.MUSIC_RANDOM_MODE;
            case LIBRARY_SHUFFLE -> Message.MUSIC_LIBRARY_RANDOM_MODE;
        };
    }

    private static Message modeDescription(JukeboxPlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ALL -> Message.MUSIC_SEQUENTIAL_MODE_DESC;
            case REPEAT_ONE -> Message.MUSIC_REPEAT_ONE_MODE_DESC;
            case SHUFFLE -> Message.MUSIC_RANDOM_MODE_DESC;
            case LIBRARY_SHUFFLE -> Message.MUSIC_LIBRARY_RANDOM_MODE_DESC;
        };
    }

    private Component musicMessage(Player player, Message message, NamedTextColor baseColor,
                                   MusicTrack track, NamedTextColor trackColor) {
        return languageService.rich(player, message, baseColor,
                RichArg.component("music", Component.text(track.details().title(), trackColor),
                        track.details().title()));
    }
}
