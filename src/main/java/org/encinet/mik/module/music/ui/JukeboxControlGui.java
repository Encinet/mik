package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFlow;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.FloatingMenuState;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.music.catalog.AudioPropertiesFormatter;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.disc.MusicDiscKeys;
import org.encinet.mik.module.music.disc.MusicDiscResolver;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackMode;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackStatus;
import org.encinet.mik.module.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.module.music.jukebox.JukeboxSoundSettings;
import org.encinet.mik.module.music.jukebox.PlaybackStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Renders the main-thread-confined jukebox queue and playback controls. */
public final class JukeboxControlGui {
    private JukeboxControlActionHandler actionHandler;

    private static final int ITEMS_PER_PAGE = 8;

    private final JukeboxQueueService queueService;
    private final MusicDiscFactory discFactory;
    private final MusicDiscResolver discResolver;
    private final JukeboxPlaybackStatus playbackStatus;
    private final JukeboxSettingsStore settingsStore;
    private final LanguageService languageService;
    private final FloatingMenuScreen<ViewState> screen;

    public JukeboxControlGui(JukeboxQueueService queueService, MusicDiscFactory discFactory,
                             MusicDiscResolver discResolver,
                             JukeboxPlaybackStatus playbackStatus,
                             JukeboxSettingsStore settingsStore,
                             LanguageService languageService) {
        this.queueService = queueService;
        this.discFactory = discFactory;
        this.discResolver = discResolver;
        this.playbackStatus = playbackStatus;
        this.settingsStore = settingsStore;
        this.languageService = languageService;
        this.screen = new FloatingMenuScreen<>("jukebox-control",
                context -> buildCurrent(context.player(), context.state()));
    }

    public void openJukeboxControl(Player player, Jukebox jukebox) {
        openJukeboxControlPage(player, jukebox, 0);
    }

    /**
     * Physical interaction keeps an already visible control session intact and
     * moves it to the player's current view. Interacting with another jukebox
     * changes the target, while interacting with the same one preserves its page.
     */
    public void openOrRepositionJukeboxControl(Player player, Jukebox jukebox) {
        JukeboxTarget target = JukeboxTarget.at(jukebox.getLocation());
        java.util.Optional<FloatingMenuFlow<ViewState>> existing = screen.flow(player);
        if (existing.isPresent()) {
            FloatingMenuFlow<ViewState> flow = existing.get();
            FloatingMenuState lifecycle = flow.handle().state();
            boolean sameTarget = flow.state().target().equals(target);
            if (lifecycle == FloatingMenuState.OPENING
                    || lifecycle == FloatingMenuState.ACTIVE) {
                if (!sameTarget) flow.setState(new ViewState(target, 0));
                flow.reanchor();
                return;
            }
            if (lifecycle == FloatingMenuState.SUSPENDED) {
                screen.open(player, sameTarget ? flow.state() : new ViewState(target, 0));
                return;
            }
        }
        screen.open(player, new ViewState(target, 0));
    }

    public void openJukeboxControlPage(Player player, Jukebox jukebox, int page) {
        screen.open(player, ViewState.at(jukebox.getLocation(), page));
    }

    /** Refreshes every player currently observing this jukebox without changing screen depth. */
    public void refreshViewers(Location location) {
        JukeboxTarget target = JukeboxTarget.at(location);
        screen.updateWhere(view -> view.target().equals(target), view -> {
            Location current = view.target().location();
            JukeboxQueueService.JukeboxState data = current == null
                    ? null : queueService.findState(current);
            int maximumPage = data == null ? 0 : pageCount(data.queueSize()) - 1;
            return view.withMaximumPage(maximumPage);
        });
    }

    /** Removes active and suspended controls when their physical jukebox disappears. */
    public void closeViewers(Location location) {
        JukeboxTarget target = JukeboxTarget.at(location);
        screen.closeWhere(view -> view.target().equals(target));
    }

    public void closeWorld(World world) {
        UUID worldId = Objects.requireNonNull(world, "world").getUID();
        screen.closeWhere(view -> view.target().worldId().equals(worldId));
    }

    private FloatingMenuDefinition buildCurrent(Player player, ViewState view) {
        JukeboxControlActionHandler actions = Objects.requireNonNull(actionHandler,
                "Jukebox control action handler has not been configured");
        Location location = view.target().location();
        if (location == null || !(location.getBlock().getState() instanceof Jukebox jukebox)) {
            return unavailableMenu(player, actions);
        }
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        List<MusicTrack> queue = data.queue();
        int totalPages = pageCount(queue.size());
        int currentPage = Math.max(0, Math.min(view.page(), totalPages - 1));
        MusicTrack currentDisc = discResolver.resolve(jukebox.getRecord());
        PlaybackStatus currentStatus = currentPlaybackStatus(jukebox, currentDisc);

        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("jukebox-control")
                .layout(FloatingMenuLayouts.verticalRegions(0.36,
                        FloatingMenuLayouts.region("queue",
                                FloatingMenuLayouts.offset(
                                        FloatingMenuLayouts.adaptiveCurvedList(
                                                2, 4, 0.42, 0.18, 0.26),
                                        -0.90, 0.0, 0.0)),
                        FloatingMenuLayouts.region("controls",
                                FloatingMenuLayouts.adaptiveCurvedGrid(
                                        5, 0.30, 0.22, 0.18))));

        menu.textDecoration("disc-info",
                FloatingMenuPose.oriented(new FloatingMenuPoint(2.75, 0.72, 1.25),
                        20.0, 0.0),
                currentDiscInfo(player, jukebox, currentDisc, currentStatus),
                FloatingMenuAppearance.TRANSPARENT, 3.6F, 3.1F, 0.66F,
                FloatingMenuDecoration.Alignment.LEFT);
        if (jukebox.hasRecord()) {
            menu.worldItemDecoration("disc",
                    location.clone().add(0.5, 1.20, 0.5),
                    0.0, 90.0, currentDiscVisual(player, jukebox, currentDisc),
                    0.78F, discMotion(currentStatus));
        }

        menu.item("select-music", Material.MUSIC_DISC_WAIT, selectMusicLabel(player))
                .region("controls")
                .primary((p, handle) -> actions.selectMusic(p, location));

        var rhythmControl = menu.item("rhythm-game", Material.TARGET,
                        Component.text(languageService.t(player, Message.MUSIC_RHYTHM_GAME),
                                        NamedTextColor.LIGHT_PURPLE)
                                .decoration(TextDecoration.BOLD, true)
                                .append(Component.newline())
                                .append(Component.text(languageService.t(player,
                                                Message.MUSIC_RHYTHM_GAME_DESCRIPTION),
                                        NamedTextColor.GRAY)
                                        .decoration(TextDecoration.BOLD, false)))
                .region("controls")
                .primary((p, handle) -> actions.openRhythmGame(p, location));
        if (currentDisc == null || currentStatus != PlaybackStatus.PLAYING) {
            rhythmControl.disabled(languageService.text(player,
                    Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
        }

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, queue.size());
        if (start == end) {
            menu.information("queue:empty",
                            Component.text(languageService.t(player, Message.MUSIC_QUEUE_EMPTY),
                                    NamedTextColor.GRAY)
                                    .append(Component.newline())
                                    .append(Component.text(languageService.t(player,
                                            Message.MUSIC_QUEUE_EMPTY_DESCRIPTION), NamedTextColor.DARK_GRAY)))
                    .region("queue");
        } else {
            for (int index = start; index < end; index++) {
                MusicTrack track = queue.get(index);
                menu.control("queue:" + track.id(), queueLabel(track, index + 1))
                        .region("queue")
                        .primary((p, handle) -> actions.queueTrack(p, location,
                                track, FloatingMenuInteraction.PRIMARY))
                        .secondary((p, handle) -> actions.queueTrack(p, location,
                                track, FloatingMenuInteraction.SECONDARY))
                        .scrollUp((p, handle) -> actions.queueTrack(p, location,
                                track, FloatingMenuInteraction.SCROLL_UP))
                        .scrollDown((p, handle) -> actions.queueTrack(p, location,
                                track, FloatingMenuInteraction.SCROLL_DOWN));
            }
        }

        JukeboxSoundSettings settings = settingsStore.read(jukebox);
        menu.item("volume", Material.NOTE_BLOCK, settingLabel(player,
                        Message.MUSIC_JUKEBOX_VOLUME, Message.MUSIC_JUKEBOX_VOLUME_VALUE,
                        settings.volumePercent()))
                .region("controls")
                .primary((p, handle) -> actions.adjustVolume(p, location,
                        FloatingMenuInteraction.PRIMARY))
                .secondary((p, handle) -> actions.adjustVolume(p, location,
                        FloatingMenuInteraction.SECONDARY))
                .scrollUp((p, handle) -> actions.adjustVolume(p, location,
                        FloatingMenuInteraction.SCROLL_UP))
                .scrollDown((p, handle) -> actions.adjustVolume(p, location,
                        FloatingMenuInteraction.SCROLL_DOWN));
        menu.item("range", Material.SPYGLASS, settingLabel(player,
                        Message.MUSIC_JUKEBOX_RANGE, Message.MUSIC_JUKEBOX_RANGE_VALUE,
                        settings.rangeBlocks()))
                .region("controls")
                .primary((p, handle) -> actions.adjustRange(p, location,
                        FloatingMenuInteraction.PRIMARY))
                .secondary((p, handle) -> actions.adjustRange(p, location,
                        FloatingMenuInteraction.SECONDARY))
                .scrollUp((p, handle) -> actions.adjustRange(p, location,
                        FloatingMenuInteraction.SCROLL_UP))
                .scrollDown((p, handle) -> actions.adjustRange(p, location,
                        FloatingMenuInteraction.SCROLL_DOWN));
        menu.item("playback-mode", modeVisual(data.playbackMode()),
                        modeLabel(player, data.playbackMode()))
                .region("controls")
                .primary((p, handle) -> actions.cycleMode(p, location));
        menu.control("play-next", playNextLabel(player, data.playbackMode()))
                .region("controls")
                .primary((p, handle) -> actions.playNext(p, location));

        if (currentPage > 0) {
            menu.navigation("page:previous",
                            Component.text("‹ " + languageService.t(player,
                                    Message.MUSIC_PREV_PAGE), NamedTextColor.YELLOW))
                    .region("controls")
                    .primary((p, handle) -> actions.openPage(p, location, currentPage - 1));
            menu.on(FloatingMenuInteraction.SCROLL_UP,
                    (p, handle, input) -> actions.openPage(p, location, currentPage - 1));
        }
        if (currentPage < totalPages - 1) {
            menu.navigation("page:next",
                            Component.text(languageService.t(player,
                                    Message.MUSIC_NEXT_PAGE) + " ›", NamedTextColor.YELLOW))
                    .region("controls")
                    .primary((p, handle) -> actions.openPage(p, location, currentPage + 1));
            menu.on(FloatingMenuInteraction.SCROLL_DOWN,
                    (p, handle, input) -> actions.openPage(p, location, currentPage + 1));
        }
        menu.item("queue:add-all", Material.CHEST,
                        Component.text(languageService.t(player, Message.MUSIC_ADD_ALL),
                                NamedTextColor.GOLD))
                .region("controls")
                .primary((p, handle) -> actions.addAll(p, location));
        menu.control("queue:clear",
                        Component.text(languageService.t(player, Message.MUSIC_CLEAR_QUEUE),
                                NamedTextColor.RED))
                .region("controls")
                .primary((p, handle) -> actions.clearQueue(p, location));
        menu.information("queue:summary", queueSummary(player, queue.size(),
                        currentPage + 1, totalPages))
                .region("controls");
        if (jukebox.hasRecord()) {
            menu.control("stop-eject",
                            Component.text(languageService.t(player, Message.MUSIC_STOP_EJECT),
                                    NamedTextColor.YELLOW))
                    .region("controls")
                    .primary((p, handle) -> actions.stopAndEject(p, location));
        }
        menu.navigation("close",
                        Component.text(languageService.t(player, Message.CLOSE), NamedTextColor.RED))
                .region("controls")
                .primary((p, handle) -> actions.close(p));

        return menu.build();
    }

    private FloatingMenuDefinition unavailableMenu(Player player,
                                                    JukeboxControlActionHandler actions) {
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("jukebox-control")
                .layout(FloatingMenuLayouts.adaptiveColumn(0.24));
        menu.information("unavailable",
                Component.text(languageService.t(player, Message.MUSIC_JUKEBOX_UNAVAILABLE),
                        NamedTextColor.RED));
        menu.navigation("close",
                        Component.text(languageService.t(player, Message.CLOSE), NamedTextColor.RED))
                .primary((p, handle) -> actions.close(p));
        return menu.build();
    }

    public void setActionHandler(JukeboxControlActionHandler actionHandler) {
        this.actionHandler = actionHandler;
    }

    private PlaybackStatus currentPlaybackStatus(Jukebox jukebox, MusicTrack currentDisc) {
        if (!jukebox.hasRecord()) return PlaybackStatus.STOPPED;
        if (currentDisc != null || MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
            return playbackStatus.status(jukebox.getBlock());
        }
        return jukebox.isPlaying() ? PlaybackStatus.PLAYING : PlaybackStatus.STOPPED;
    }

    private ItemStack currentDiscVisual(Player player, Jukebox jukebox, MusicTrack currentDisc) {
        return currentDisc == null
                ? jukebox.getRecord().asOne()
                : discFactory.createDisplayDisc(currentDisc, false, player);
    }

    private Component currentDiscInfo(Player player, Jukebox jukebox,
                                      MusicTrack currentDisc, PlaybackStatus status) {
        if (!jukebox.hasRecord()) {
            return Component.text(languageService.t(player, Message.MUSIC_NO_DISC),
                    NamedTextColor.GRAY);
        }
        if (currentDisc == null && MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
            return Component.text(languageService.t(player, Message.MUSIC_UNAVAILABLE_DISC),
                            NamedTextColor.RED)
                    .append(Component.newline())
                    .append(Component.text(languageService.t(player,
                            Message.MUSIC_UNAVAILABLE_DISC_DESCRIPTION), NamedTextColor.GRAY));
        }

        Component name = currentDisc == null
                ? jukebox.getRecord().effectiveName().colorIfAbsent(NamedTextColor.WHITE)
                : Component.text(truncate(currentDisc.details().title(), 64), NamedTextColor.WHITE);
        Component information = name.decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, Message.MUSIC_PLAYBACK_STATUS,
                                languageService.t(player, statusMessage(status))))
                        .color(statusColor(status))
                        .decoration(TextDecoration.ITALIC, false));
        if (currentDisc == null) return information;

        TrackDetails details = currentDisc.details();
        if (details.artist() != null) {
            information = information.append(metadataLine(player,
                    Message.MUSIC_ARTIST, details.artist()));
        } else if (details.originalAuthor() != null) {
            information = information.append(metadataLine(player,
                    Message.MUSIC_ORIGINAL_AUTHOR, details.originalAuthor()));
        }
        if (details.album() != null) {
            information = information.append(metadataLine(player,
                    Message.MUSIC_ALBUM, details.album()));
        }
        String duration = AudioPropertiesFormatter.duration(details.audio().duration());
        if (duration != null) {
            information = information.append(metadataLine(player,
                    Message.MUSIC_DURATION, duration));
        }
        return information;
    }

    private Component metadataLine(Player player, Message message, String value) {
        return Component.newline().append(Component.text(languageService.t(player, message, value),
                        NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
    }

    private static FloatingMenuDecoration.Motion discMotion(PlaybackStatus status) {
        return switch (status) {
            case PLAYING -> FloatingMenuDecoration.Motion.SPIN;
            case LOADING -> FloatingMenuDecoration.Motion.BOB;
            case STOPPED -> FloatingMenuDecoration.Motion.NONE;
        };
    }

    private Component queueLabel(MusicTrack music, int rank) {
        Component label = Component.text(rank + " · " + truncate(music.details().title(), 20),
                NamedTextColor.WHITE);
        String artist = music.details().artist() != null
                ? music.details().artist() : music.details().originalAuthor();
        if (artist != null && !artist.isBlank()) {
            label = label.append(Component.newline())
                    .append(Component.text(truncate(artist, 18), NamedTextColor.DARK_GRAY));
        }
        return label;
    }

    private Component selectMusicLabel(Player player) {
        return Component.text(languageService.t(player, Message.MUSIC_SELECT_MUSIC),
                        NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true);
    }

    private ItemStack modeVisual(JukeboxPlaybackMode mode) {
        return new ItemStack(modeMaterial(mode));
    }

    private Component modeLabel(Player player, JukeboxPlaybackMode mode) {
        String name = languageService.t(player, modeName(mode));
        return Component.text(name, modeColor(mode))
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, modeDescription(mode)),
                                NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false));
    }

    private Component settingLabel(Player player, Message name, Message valueMessage, int value) {
        return Component.text(languageService.t(player, name), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, valueMessage, value))
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, false));
    }

    private Component playNextLabel(Player player, JukeboxPlaybackMode mode) {
        boolean shuffle = mode == JukeboxPlaybackMode.SHUFFLE
                || mode == JukeboxPlaybackMode.LIBRARY_SHUFFLE;
        return Component.text(languageService.t(player, shuffle
                        ? Message.MUSIC_PLAY_RANDOM : Message.MUSIC_PLAY_NEXT),
                NamedTextColor.AQUA);
    }

    private Component queueSummary(Player player, int size, int page, int totalPages) {
        return Component.text(languageService.t(player, Message.MUSIC_QUEUE_SUMMARY, size),
                        NamedTextColor.GOLD)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                        Message.MUSIC_QUEUE_SUMMARY_DESCRIPTION, page, totalPages),
                        NamedTextColor.GRAY));
    }

    private static Message statusMessage(PlaybackStatus status) {
        return switch (status) {
            case STOPPED -> Message.MUSIC_STOPPED;
            case LOADING -> Message.MUSIC_LOADING_TITLE;
            case PLAYING -> Message.MUSIC_PLAYING;
        };
    }

    private static NamedTextColor statusColor(PlaybackStatus status) {
        return switch (status) {
            case STOPPED -> NamedTextColor.YELLOW;
            case LOADING -> NamedTextColor.AQUA;
            case PLAYING -> NamedTextColor.GREEN;
        };
    }

    private static Material modeMaterial(JukeboxPlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ALL -> Material.REPEATER;
            case REPEAT_ONE -> Material.MUSIC_DISC_11;
            case SHUFFLE -> Material.ENDER_EYE;
            case LIBRARY_SHUFFLE -> Material.CHISELED_BOOKSHELF;
        };
    }

    private static NamedTextColor modeColor(JukeboxPlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ALL -> NamedTextColor.GREEN;
            case REPEAT_ONE -> NamedTextColor.GOLD;
            case SHUFFLE -> NamedTextColor.LIGHT_PURPLE;
            case LIBRARY_SHUFFLE -> NamedTextColor.AQUA;
        };
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

    private static String truncate(String text, int maximum) {
        if (text.length() <= maximum) {
            return text;
        }
        int end = maximum - 3;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "...";
    }

    public static int pageCount(int queueSize) {
        return Math.max(1, (Math.max(0, queueSize) + ITEMS_PER_PAGE - 1)
                / ITEMS_PER_PAGE);
    }

    private record ViewState(JukeboxTarget target, int page) {
        private static ViewState at(Location location, int page) {
            return new ViewState(JukeboxTarget.at(location), Math.max(0, page));
        }

        private ViewState withMaximumPage(int maximumPage) {
            int clamped = Math.min(page, Math.max(0, maximumPage));
            return clamped == page ? this : new ViewState(target, clamped);
        }
    }

    private record JukeboxTarget(UUID worldId, int x, int y, int z) {
        private static JukeboxTarget at(Location location) {
            Objects.requireNonNull(location, "location");
            World world = Objects.requireNonNull(location.getWorld(), "location world");
            return new JukeboxTarget(world.getUID(), location.getBlockX(),
                    location.getBlockY(), location.getBlockZ());
        }

        private Location location() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z);
        }
    }
}
