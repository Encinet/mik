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
import org.encinet.mik.module.menu.FloatingMenuLayout;
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
import org.encinet.mik.module.music.jukebox.JukeboxExperienceMode;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackMode;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackSnapshot;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackStatus;
import org.encinet.mik.module.music.jukebox.JukeboxRhythmReadiness;
import org.encinet.mik.module.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.module.music.jukebox.JukeboxSoundSettings;
import org.encinet.mik.module.music.jukebox.PlaybackStatus;
import org.encinet.mik.module.music.rhythm.RhythmCalibrationStatus;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Renders the main-thread-confined jukebox queue and playback controls. */
public final class JukeboxControlGui {
    private JukeboxControlActionHandler actionHandler;

    private static final int ITEMS_PER_PAGE = 8;
    private static final int PLAYBACK_PROGRESS_REFRESH_TICKS = 5;
    private static final int PLAYBACK_PROGRESS_SEGMENTS = 12;

    private final JukeboxQueueService queueService;
    private final MusicDiscFactory discFactory;
    private final MusicDiscResolver discResolver;
    private final JukeboxPlaybackStatus playbackStatus;
    private final JukeboxSettingsStore settingsStore;
    private final RhythmCalibrationStatus calibrationStatus;
    private final LanguageService languageService;
    private final FloatingMenuScreen<ViewState> screen;

    public JukeboxControlGui(JukeboxQueueService queueService, MusicDiscFactory discFactory,
                             MusicDiscResolver discResolver,
                             JukeboxPlaybackStatus playbackStatus,
                             JukeboxSettingsStore settingsStore,
                             RhythmCalibrationStatus calibrationStatus,
                             LanguageService languageService) {
        this.queueService = queueService;
        this.discFactory = discFactory;
        this.discResolver = discResolver;
        this.playbackStatus = playbackStatus;
        this.settingsStore = settingsStore;
        this.calibrationStatus = Objects.requireNonNull(
                calibrationStatus, "calibrationStatus");
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
        ControlView control = controlView(jukebox, view.page());
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("jukebox-control")
                .layout(controlLayout(control.experienceMode()))
                .refreshWhenChanged(PLAYBACK_PROGRESS_REFRESH_TICKS,
                        ignored -> playbackRevision(view),
                        (p, handle) -> screen.flow(p)
                                .ifPresent(FloatingMenuFlow::redraw));

        addDiscPresentation(player, menu, control);
        addExperienceModeControl(player, menu, actions, control);
        switch (control.experienceMode()) {
            case MUSIC -> addMusicModeControls(player, menu, actions, control);
            case RHYTHM -> addRhythmModeControls(player, menu, actions, control);
        }
        addSoundControls(player, menu, actions, control);
        addCommonNavigation(player, menu, actions, control);
        return menu.build();
    }

    private ControlView controlView(Jukebox jukebox, int requestedPage) {
        Location location = jukebox.getLocation();
        JukeboxQueueService.JukeboxState data = queueService.state(location);
        List<MusicTrack> queue = data.queue();
        int totalPages = pageCount(queue.size());
        int currentPage = Math.max(0, Math.min(requestedPage, totalPages - 1));
        MusicTrack currentDisc = discResolver.resolve(jukebox.getRecord());
        JukeboxPlaybackSnapshot currentPlayback = currentPlayback(jukebox);
        JukeboxSoundSettings settings = settingsStore.read(jukebox);
        JukeboxExperienceMode experienceMode = settingsStore.readExperienceMode(jukebox);
        JukeboxExperienceMode activeExperienceMode = playbackStatus
                .activeExperienceMode(jukebox.getBlock()).orElse(experienceMode);
        JukeboxRhythmReadiness rhythmReadiness = playbackStatus
                .rhythmReadiness(jukebox.getBlock());
        return new ControlView(location, jukebox, data, queue, currentPage,
                totalPages, currentDisc, currentPlayback, settings,
                experienceMode, activeExperienceMode, rhythmReadiness);
    }

    private static FloatingMenuLayout controlLayout(JukeboxExperienceMode mode) {
        FloatingMenuLayout content = mode == JukeboxExperienceMode.MUSIC
                ? FloatingMenuLayouts.adaptiveCurvedList(2, 4,
                        0.42, 0.18, 0.26)
                : FloatingMenuLayouts.adaptiveColumn(0.18);
        int controlColumns = mode == JukeboxExperienceMode.MUSIC ? 5 : 4;
        return FloatingMenuLayouts.verticalRegions(0.36,
                FloatingMenuLayouts.region("content",
                        FloatingMenuLayouts.offset(content, -0.90, 0.0, 0.0)),
                FloatingMenuLayouts.region("controls",
                        FloatingMenuLayouts.adaptiveCurvedGrid(
                                controlColumns, 0.30, 0.22, 0.18)));
    }

    private void addDiscPresentation(Player player,
                                     FloatingMenuDefinition.Builder menu,
                                     ControlView control) {
        menu.textDecoration("disc-info",
                FloatingMenuPose.oriented(new FloatingMenuPoint(2.75, 0.72, 1.25),
                        20.0, 0.0),
                currentDiscInfo(player, control.jukebox(), control.currentDisc(),
                        control.playback(), control.activeExperienceMode()),
                FloatingMenuAppearance.TRANSPARENT, 3.6F, 3.1F, 0.66F,
                FloatingMenuDecoration.Alignment.LEFT);
        if (control.jukebox().hasRecord()) {
            menu.worldItemDecoration("disc",
                    control.location().clone().add(0.5, 1.20, 0.5),
                    0.0, 90.0, currentDiscVisual(player,
                            control.jukebox(), control.currentDisc()),
                    0.78F, discMotion(control.status()));
        }
    }

    private void addExperienceModeControl(Player player,
                                          FloatingMenuDefinition.Builder menu,
                                          JukeboxControlActionHandler actions,
                                          ControlView control) {
        menu.item("experience-mode", experienceModeMaterial(control.experienceMode()),
                        experienceModeLabel(player, control.experienceMode()))
                .region("controls")
                .primary((p, handle) -> actions.cycleExperienceMode(
                        p, control.location()));
    }

    private void addMusicModeControls(Player player,
                                      FloatingMenuDefinition.Builder menu,
                                      JukeboxControlActionHandler actions,
                                      ControlView control) {
        menu.item("select-music", Material.MUSIC_DISC_WAIT, selectMusicLabel(player))
                .region("controls")
                .primary((p, handle) -> actions.selectMusic(p, control.location()));
        addQueue(player, menu, actions, control);
        menu.item("playback-mode", modeVisual(control.data().playbackMode()),
                        modeLabel(player, control.data().playbackMode()))
                .region("controls")
                .primary((p, handle) -> actions.cycleMode(p, control.location()));
        menu.control("play-next", playNextLabel(player,
                        control.data().playbackMode()))
                .region("controls")
                .primary((p, handle) -> actions.playNext(p, control.location()));
        addQueueNavigation(player, menu, actions, control);
        menu.control("queue:clear",
                        Component.text(languageService.t(player,
                                Message.MUSIC_CLEAR_QUEUE), NamedTextColor.RED))
                .region("controls")
                .primary((p, handle) -> actions.clearQueue(p, control.location()));
        menu.information("queue:summary", queueSummary(player,
                        control.queue().size(), control.currentPage() + 1,
                        control.totalPages()))
                .region("controls");
    }

    private void addQueue(Player player, FloatingMenuDefinition.Builder menu,
                          JukeboxControlActionHandler actions, ControlView control) {
        int start = control.currentPage() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, control.queue().size());
        if (start == end) {
            menu.information("queue:empty",
                            Component.text(languageService.t(player, Message.MUSIC_QUEUE_EMPTY),
                                    NamedTextColor.GRAY)
                                    .append(Component.newline())
                                    .append(Component.text(languageService.t(player,
                                            Message.MUSIC_QUEUE_EMPTY_DESCRIPTION), NamedTextColor.DARK_GRAY)))
                    .region("content");
        } else {
            for (int index = start; index < end; index++) {
                MusicTrack track = control.queue().get(index);
                menu.control("queue:" + track.id(), queueLabel(track, index + 1))
                        .region("content")
                        .primary((p, handle) -> actions.queueTrack(p, control.location(),
                                track, FloatingMenuInteraction.PRIMARY))
                        .secondary((p, handle) -> actions.queueTrack(p, control.location(),
                                track, FloatingMenuInteraction.SECONDARY))
                        .scrollUp((p, handle) -> actions.queueTrack(p, control.location(),
                                track, FloatingMenuInteraction.SCROLL_UP))
                        .scrollDown((p, handle) -> actions.queueTrack(p, control.location(),
                                track, FloatingMenuInteraction.SCROLL_DOWN));
            }
        }
    }

    private void addRhythmModeControls(Player player,
                                       FloatingMenuDefinition.Builder menu,
                                       JukeboxControlActionHandler actions,
                                       ControlView control) {
        boolean ready = control.rhythmReady();
        boolean calibrated = calibrationStatus.hasCompletedLatencyCalibration(player);
        boolean rhythmTrackAvailable = control.currentDisc() != null
                && control.activeExperienceMode() == JukeboxExperienceMode.RHYTHM;
        menu.information("rhythm:status",
                        rhythmModeStatusLabel(player, control, calibrated))
                .region("content");
        menu.item("select-rhythm-track", Material.MUSIC_DISC_5,
                        selectRhythmTrackLabel(player))
                .region("controls")
                .primary((p, handle) -> actions.selectMusic(p, control.location()));

        var rhythmControl = menu.item("rhythm-game", Material.TARGET,
                        rhythmGameLabel(player, control, calibrated))
                .region("controls")
                .primary((p, handle) -> actions.openRhythmGame(
                        p, control.location()));
        if (!rhythmTrackAvailable) {
            rhythmControl.disabled(rhythmUnavailableReason(player, control));
        }

        menu.item("latency-calibration", Material.REPEATER,
                        latencyCalibrationLabel(player, calibrated))
                .region("controls")
                .selected(calibrated)
                .primary((p, handle) -> actions.openLatencyCalibration(
                        p, control.location()))
                .secondary((p, handle) -> actions.resetLatencyCalibration(
                        p, control.location()));
    }

    private void addSoundControls(Player player,
                                  FloatingMenuDefinition.Builder menu,
                                  JukeboxControlActionHandler actions,
                                  ControlView control) {
        menu.item("volume", Material.NOTE_BLOCK, settingLabel(player,
                        Message.MUSIC_JUKEBOX_VOLUME, Message.MUSIC_JUKEBOX_VOLUME_VALUE,
                        control.settings().volumePercent()))
                .region("controls")
                .primary((p, handle) -> actions.adjustVolume(p, control.location(),
                        FloatingMenuInteraction.PRIMARY))
                .secondary((p, handle) -> actions.adjustVolume(p, control.location(),
                        FloatingMenuInteraction.SECONDARY))
                .scrollUp((p, handle) -> actions.adjustVolume(p, control.location(),
                        FloatingMenuInteraction.SCROLL_UP))
                .scrollDown((p, handle) -> actions.adjustVolume(p, control.location(),
                        FloatingMenuInteraction.SCROLL_DOWN));
        menu.item("range", Material.SPYGLASS, settingLabel(player,
                        Message.MUSIC_JUKEBOX_RANGE, Message.MUSIC_JUKEBOX_RANGE_VALUE,
                        control.settings().rangeBlocks()))
                .region("controls")
                .primary((p, handle) -> actions.adjustRange(p, control.location(),
                        FloatingMenuInteraction.PRIMARY))
                .secondary((p, handle) -> actions.adjustRange(p, control.location(),
                        FloatingMenuInteraction.SECONDARY))
                .scrollUp((p, handle) -> actions.adjustRange(p, control.location(),
                        FloatingMenuInteraction.SCROLL_UP))
                .scrollDown((p, handle) -> actions.adjustRange(p, control.location(),
                        FloatingMenuInteraction.SCROLL_DOWN));
    }

    private void addQueueNavigation(Player player,
                                    FloatingMenuDefinition.Builder menu,
                                    JukeboxControlActionHandler actions,
                                    ControlView control) {
        if (control.currentPage() > 0) {
            menu.navigation("page:previous",
                            Component.text("‹ " + languageService.t(player,
                                    Message.MUSIC_PREV_PAGE), NamedTextColor.YELLOW))
                    .region("controls")
                    .primary((p, handle) -> actions.openPage(p, control.location(),
                            control.currentPage() - 1));
            menu.on(FloatingMenuInteraction.SCROLL_UP,
                    (p, handle, input) -> actions.openPage(p, control.location(),
                            control.currentPage() - 1));
        }
        if (control.currentPage() < control.totalPages() - 1) {
            menu.navigation("page:next",
                            Component.text(languageService.t(player,
                                    Message.MUSIC_NEXT_PAGE) + " ›", NamedTextColor.YELLOW))
                    .region("controls")
                    .primary((p, handle) -> actions.openPage(p, control.location(),
                            control.currentPage() + 1));
            menu.on(FloatingMenuInteraction.SCROLL_DOWN,
                    (p, handle, input) -> actions.openPage(p, control.location(),
                            control.currentPage() + 1));
        }
    }

    private void addCommonNavigation(Player player,
                                     FloatingMenuDefinition.Builder menu,
                                     JukeboxControlActionHandler actions,
                                     ControlView control) {
        if (control.jukebox().hasRecord()) {
            menu.control("stop-eject",
                            Component.text(languageService.t(player, Message.MUSIC_STOP_EJECT),
                                    NamedTextColor.YELLOW))
                    .region("controls")
                    .primary((p, handle) -> actions.stopAndEject(
                            p, control.location()));
        }
        menu.navigation("close",
                        Component.text(languageService.t(player, Message.CLOSE), NamedTextColor.RED))
                .region("controls")
                .primary((p, handle) -> actions.close(p));
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

    private JukeboxPlaybackSnapshot currentPlayback(Jukebox jukebox) {
        if (!jukebox.hasRecord()) {
            return new JukeboxPlaybackSnapshot(PlaybackStatus.STOPPED, 0L);
        }
        if (MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
            return playbackStatus.snapshot(jukebox.getBlock());
        }
        return new JukeboxPlaybackSnapshot(currentVanillaPlaybackStatus(jukebox), 0L);
    }

    private static PlaybackStatus currentVanillaPlaybackStatus(Jukebox jukebox) {
        return jukebox.isPlaying() ? PlaybackStatus.PLAYING : PlaybackStatus.STOPPED;
    }

    private PlaybackRevision playbackRevision(ViewState view) {
        Location location = view.target().location();
        if (location == null || !(location.getBlock().getState() instanceof Jukebox jukebox)) {
            return PlaybackRevision.UNAVAILABLE;
        }
        JukeboxPlaybackSnapshot playback = currentPlayback(jukebox);
        long elapsedSeconds = playback.status() == PlaybackStatus.PLAYING
                ? playback.positionMillis() / 1_000L : 0L;
        JukeboxExperienceMode configured = settingsStore.readExperienceMode(jukebox);
        JukeboxExperienceMode active = playbackStatus
                .activeExperienceMode(jukebox.getBlock()).orElse(configured);
        return new PlaybackRevision(playback.status(), elapsedSeconds,
                configured, active,
                playbackStatus.rhythmReadiness(jukebox.getBlock()));
    }

    private ItemStack currentDiscVisual(Player player, Jukebox jukebox, MusicTrack currentDisc) {
        return currentDisc == null
                ? jukebox.getRecord().asOne()
                : discFactory.createDisplayDisc(currentDisc, false, player);
    }

    private Component currentDiscInfo(Player player, Jukebox jukebox,
                                      MusicTrack currentDisc,
                                      JukeboxPlaybackSnapshot playback,
                                      JukeboxExperienceMode experienceMode) {
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
        PlaybackStatus status = playback.status();
        Component information = name.decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, Message.MUSIC_PLAYBACK_STATUS,
                                languageService.t(player, statusMessage(status))))
                        .color(statusColor(status))
                        .decoration(TextDecoration.ITALIC, false));
        Message experienceName = experienceMode == JukeboxExperienceMode.MUSIC
                ? Message.MUSIC_JUKEBOX_MUSIC_MODE
                : Message.MUSIC_JUKEBOX_RHYTHM_MODE;
        information = information.append(Component.newline())
                .append(languageService.text(player,
                        Message.MUSIC_JUKEBOX_USAGE_MODE, NamedTextColor.GRAY))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player, experienceName,
                        experienceMode == JukeboxExperienceMode.MUSIC
                                ? NamedTextColor.AQUA
                                : NamedTextColor.LIGHT_PURPLE));
        if (status == PlaybackStatus.LOADING
                && experienceMode == JukeboxExperienceMode.RHYTHM) {
            information = information.append(Component.newline())
                    .append(languageService.text(player,
                            Message.MUSIC_RHYTHM_PREPARING,
                            NamedTextColor.AQUA));
        }
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
        Duration trackDuration = details.audio().duration();
        if (status == PlaybackStatus.PLAYING) {
            information = information.append(playbackProgress(
                    playback.positionMillis(), trackDuration));
        } else {
            String duration = AudioPropertiesFormatter.duration(trackDuration);
            if (duration == null) return information;
            information = information.append(metadataLine(player,
                    Message.MUSIC_DURATION, duration));
        }
        return information;
    }

    static Component playbackProgress(long positionMillis, Duration duration) {
        String elapsed = playbackTime(positionMillis);
        Component line = Component.newline()
                .append(Component.text("▶ ", NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false));
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return line.append(Component.text(elapsed, NamedTextColor.GRAY)
                    .decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false));
        }

        long durationMillis = Math.max(1L, duration.toMillis());
        long boundedPosition = Math.min(Math.max(0L, positionMillis), durationMillis);
        int completed = completedProgressSegments(boundedPosition, durationMillis);
        String total = AudioPropertiesFormatter.duration(duration);
        if (boundedPosition == durationMillis) elapsed = total;
        return line
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("=".repeat(completed), NamedTextColor.AQUA))
                .append(Component.text("-".repeat(PLAYBACK_PROGRESS_SEGMENTS - completed),
                        NamedTextColor.DARK_GRAY))
                .append(Component.text("] " + elapsed + " / " + total,
                                NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false));
    }

    static String playbackTime(long positionMillis) {
        long seconds = Math.max(0L, positionMillis) / 1_000L;
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainingSeconds = seconds % 60L;
        return hours > 0
                ? "%d:%02d:%02d".formatted(hours, minutes, remainingSeconds)
                : "%d:%02d".formatted(minutes, remainingSeconds);
    }

    static int completedProgressSegments(long positionMillis, long durationMillis) {
        if (durationMillis <= 0L) return 0;
        double ratio = Math.clamp(positionMillis / (double) durationMillis, 0.0, 1.0);
        return (int) Math.floor(ratio * PLAYBACK_PROGRESS_SEGMENTS);
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

    private Component selectRhythmTrackLabel(Player player) {
        return languageService.text(player, Message.MUSIC_RHYTHM_SELECT_TRACK,
                        NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true);
    }

    private Component rhythmModeStatusLabel(Player player, ControlView control,
                                            boolean calibrated) {
        return languageService.text(player, Message.MUSIC_JUKEBOX_RHYTHM_MODE,
                        NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(rhythmStatusLine(player, control))
                .append(Component.newline())
                .append(calibrationStatusLine(player, calibrated));
    }

    private Component calibrationStatusLine(Player player, boolean calibrated) {
        return languageService.text(player,
                calibrated ? Message.MUSIC_RHYTHM_CALIBRATION_COMPLETE
                        : Message.MUSIC_RHYTHM_CALIBRATION_REQUIRED,
                calibrated ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
    }

    private Component rhythmStatusLine(Player player, ControlView control) {
        if (control.readiness() == JukeboxRhythmReadiness.WAITING_FOR_PLAYER) {
            return languageService.text(player,
                    Message.MUSIC_RHYTHM_WAITING_FOR_PLAYER, NamedTextColor.YELLOW);
        }
        if (control.rhythmReady()) {
            return languageService.text(player, Message.MUSIC_RHYTHM_READY,
                    NamedTextColor.GREEN);
        }
        return switch (control.readiness()) {
            case WAITING_FOR_PLAYER -> languageService.text(player,
                    Message.MUSIC_RHYTHM_WAITING_FOR_PLAYER, NamedTextColor.YELLOW);
            case PREPARING -> languageService.text(player,
                    Message.MUSIC_RHYTHM_PREPARING, NamedTextColor.AQUA);
            case NO_BEATS -> languageService.text(player,
                    Message.MUSIC_RHYTHM_NO_BEATS, NamedTextColor.RED);
            case READY -> languageService.text(player,
                    Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED);
            case UNAVAILABLE -> languageService.text(player,
                    control.currentDisc() != null
                            && control.activeExperienceMode() == JukeboxExperienceMode.RHYTHM
                            ? Message.MUSIC_RHYTHM_WAITING_FOR_PLAYER
                            : Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK,
                    NamedTextColor.RED);
        };
    }

    private Component rhythmUnavailableReason(Player player, ControlView control) {
        Message message = switch (control.readiness()) {
            case WAITING_FOR_PLAYER -> Message.MUSIC_RHYTHM_WAITING_FOR_PLAYER;
            case PREPARING -> Message.MUSIC_RHYTHM_PREPARING;
            case NO_BEATS -> Message.MUSIC_RHYTHM_NO_BEATS;
            case READY -> control.activeExperienceMode() == JukeboxExperienceMode.RHYTHM
                    ? Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK
                    : Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK;
            case UNAVAILABLE -> control.currentDisc() == null
                    ? Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK
                    : Message.MUSIC_RHYTHM_WAITING_FOR_PLAYER;
        };
        NamedTextColor color = message == Message.MUSIC_RHYTHM_PREPARING
                ? NamedTextColor.AQUA
                : message == Message.MUSIC_RHYTHM_WAITING_FOR_PLAYER
                ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return languageService.text(player, message, color);
    }

    private ItemStack modeVisual(JukeboxPlaybackMode mode) {
        return new ItemStack(modeMaterial(mode));
    }

    private Component modeLabel(Player player, JukeboxPlaybackMode mode) {
        String name = languageService.t(player, modeName(mode));
        return languageService.text(player, Message.MUSIC_JUKEBOX_PLAY_ORDER,
                        NamedTextColor.GRAY)
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(name, modeColor(mode)))
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, modeDescription(mode)),
                                NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false));
    }

    private Component experienceModeLabel(Player player, JukeboxExperienceMode mode) {
        Message name = mode == JukeboxExperienceMode.MUSIC
                ? Message.MUSIC_JUKEBOX_MUSIC_MODE : Message.MUSIC_JUKEBOX_RHYTHM_MODE;
        NamedTextColor color = mode == JukeboxExperienceMode.MUSIC
                ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE;
        return languageService.text(player, Message.MUSIC_JUKEBOX_USAGE_MODE,
                        NamedTextColor.GRAY)
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player, name, color))
                .decoration(TextDecoration.BOLD, true);
    }

    private Component rhythmGameLabel(Player player, ControlView control,
                                      boolean calibrated) {
        Component label = languageService.text(player, Message.MUSIC_RHYTHM_START_GAME,
                        NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline());
        boolean trackAvailable = control.currentDisc() != null
                && control.activeExperienceMode() == JukeboxExperienceMode.RHYTHM;
        Message detail = !trackAvailable
                ? Message.MUSIC_RHYTHM_GAME_DESCRIPTION
                : !calibrated
                ? Message.MUSIC_RHYTHM_CALIBRATION_REQUIRED
                : !control.rhythmReady()
                && control.readiness() == JukeboxRhythmReadiness.WAITING_FOR_PLAYER
                ? Message.MUSIC_RHYTHM_WAITING_FOR_PLAYER
                : !control.rhythmReady() ? Message.MUSIC_RHYTHM_PREPARING
                : Message.MUSIC_RHYTHM_READY;
        return label.append(languageService.text(player, detail,
                        !trackAvailable
                                ? NamedTextColor.RED
                                : !calibrated
                                ? NamedTextColor.YELLOW
                                : !control.rhythmReady()
                                && control.readiness() == JukeboxRhythmReadiness.NO_BEATS
                                ? NamedTextColor.RED
                                : !control.rhythmReady()
                                ? NamedTextColor.YELLOW
                                : NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, false));
    }

    private Component latencyCalibrationLabel(Player player, boolean calibrated) {
        return languageService.text(player, Message.MUSIC_RHYTHM_CALIBRATION,
                        NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(calibrationStatusLine(player, calibrated)
                        .decoration(TextDecoration.BOLD, false));
    }

    private static ItemStack experienceModeMaterial(JukeboxExperienceMode mode) {
        return new ItemStack(mode == JukeboxExperienceMode.MUSIC
                ? Material.JUKEBOX : Material.CALIBRATED_SCULK_SENSOR);
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

    private record PlaybackRevision(PlaybackStatus status, long elapsedSeconds,
                                    JukeboxExperienceMode configuredMode,
                                    JukeboxExperienceMode activeMode,
                                    JukeboxRhythmReadiness readiness) {
        private static final PlaybackRevision UNAVAILABLE =
                new PlaybackRevision(PlaybackStatus.STOPPED, 0L,
                        JukeboxExperienceMode.MUSIC,
                        JukeboxExperienceMode.MUSIC,
                        JukeboxRhythmReadiness.UNAVAILABLE);
    }

    private record ControlView(Location location, Jukebox jukebox,
                               JukeboxQueueService.JukeboxState data,
                               List<MusicTrack> queue, int currentPage,
                               int totalPages, MusicTrack currentDisc,
                               JukeboxPlaybackSnapshot playback,
                               JukeboxSoundSettings settings,
                               JukeboxExperienceMode experienceMode,
                               JukeboxExperienceMode activeExperienceMode,
                               JukeboxRhythmReadiness readiness) {
        private ControlView {
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(jukebox, "jukebox");
            Objects.requireNonNull(data, "data");
            queue = List.copyOf(Objects.requireNonNull(queue, "queue"));
            Objects.requireNonNull(playback, "playback");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(experienceMode, "experienceMode");
            Objects.requireNonNull(activeExperienceMode, "activeExperienceMode");
            Objects.requireNonNull(readiness, "readiness");
        }

        private PlaybackStatus status() {
            return playback.status();
        }

        private boolean rhythmReady() {
            return status() == PlaybackStatus.PLAYING
                    && activeExperienceMode == JukeboxExperienceMode.RHYTHM
                    && readiness.playable();
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
