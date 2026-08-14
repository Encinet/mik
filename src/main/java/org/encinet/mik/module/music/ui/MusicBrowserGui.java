package org.encinet.mik.module.music.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuContext;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.music.online.MusicSearchResult;
import org.encinet.mik.module.music.online.LxSourceService;
import org.encinet.mik.module.music.online.OnlineMusicRequestLimiter;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.AudioPropertiesFormatter;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackPool;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.catalog.TrackTarget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Renders the music browser and publishes asynchronous search results into player sessions. */
public final class MusicBrowserGui {

    private static final int ITEMS_PER_PAGE = 18;
    private static final int SEARCH_LIMIT = 200;
    private static final int TRACK_TITLE_DISPLAY_LIMIT = 24;
    private static final int TRACK_ARTIST_DISPLAY_LIMIT = 32;

    private final JavaPlugin plugin;
    private final MusicLibrary musicLibrary;
    private final MusicTrackPool trackPool;
    private final LxSourceService onlineSource;
    private final OnlineMusicRequestLimiter requestLimiter;
    private final Predicate<MusicTrack> cachedTrack;
    private final MusicPlaybackStats playbackStats;
    private final LanguageService languageService;
    private final MusicDiscFactory discFactory;
    private final MusicBrowserSessions sessions = new MusicBrowserSessions();
    private final FloatingMenuScreen<MusicBrowserSessions.Session> screen;
    private MusicBrowserActionHandler actionHandler;

    public MusicBrowserGui(JavaPlugin plugin, MusicLibrary musicLibrary,
                           MusicTrackPool trackPool,
                           LxSourceService onlineSource,
                           OnlineMusicRequestLimiter requestLimiter,
                           Predicate<MusicTrack> cachedTrack,
                           MusicPlaybackStats playbackStats,
                           LanguageService languageService,
                           MusicDiscFactory discFactory) {
        this.plugin = plugin;
        this.musicLibrary = musicLibrary;
        this.trackPool = trackPool;
        this.onlineSource = onlineSource;
        this.requestLimiter = requestLimiter;
        this.cachedTrack = cachedTrack;
        this.playbackStats = playbackStats;
        this.languageService = languageService;
        this.discFactory = discFactory;
        this.screen = new FloatingMenuScreen<>("music-browser",
                ignored -> new MusicBrowserSessions.Session(),
                this::buildCurrent);
    }

    public void openMenu(Player player) {
        showLibrary(player, 0);
    }

    public void setActionHandler(MusicBrowserActionHandler actionHandler) {
        this.actionHandler = actionHandler;
    }

    public void showLibrary(Player player, int page) {
        MusicBrowserSessions.Session state = currentOrNew(player.getUniqueId());
        sessions.showLibrary(player.getUniqueId(), state, trackPool.tracks(), page, playbackStats);
        screen.open(player, state);
    }

    public void searchMusic(Player player, String keyword) {
        String normalized;
        try {
            normalized = normalizeKeyword(keyword);
        } catch (IllegalArgumentException exception) {
            sendKeywordError(player, keyword);
            return;
        }
        OnlineMusicRequestLimiter.Decision decision = requestLimiter.tryAcquire(
                player.getUniqueId(), OnlineMusicRequestLimiter.Operation.SEARCH);
        if (!decision.allowed()) {
            player.sendMessage(languageService.text(player, Message.MUSIC_ONLINE_RATE_LIMITED,
                    NamedTextColor.YELLOW, decision.retryAfterSeconds()));
            return;
        }
        MusicBrowserSessions.Session state = currentOrNew(player.getUniqueId());
        sessions.beginSearch(player.getUniqueId(), state, normalized);
        int generation = state.generation();
        player.sendMessage(languageService.text(player, Message.MUSIC_ONLINE_SEARCHING,
                NamedTextColor.YELLOW, normalized));
        screen.open(player, state);

        List<MusicTrack> localMatches = musicLibrary.tracks().stream()
                .filter(track -> MusicSearchRanker.matches(normalized, track)).toList();
        onlineSource.searchMusic(normalized, 1, SEARCH_LIMIT)
                .whenComplete((result, error) -> runOnMainThread(() -> {
                    if (!isCurrent(player, state, generation)) {
                        return;
                    }
                    if (error != null) {
                        showSearchResult(player, state, generation, normalized, localMatches,
                                new MusicSearchResult<>(List.of(), 0, List.of(rootMessage(error))));
                        return;
                    }
                    showSearchResult(player, state, generation, normalized, localMatches, result);
                }));
    }

    public void importPlaylist(Player player, String source, String reference) {
        OnlineMusicRequestLimiter.Decision decision = requestLimiter.tryAcquire(
                player.getUniqueId(), OnlineMusicRequestLimiter.Operation.SEARCH);
        if (!decision.allowed()) {
            player.sendMessage(languageService.text(player, Message.MUSIC_ONLINE_RATE_LIMITED,
                    NamedTextColor.YELLOW, decision.retryAfterSeconds()));
            return;
        }
        MusicBrowserSessions.Session state = currentOrNew(player.getUniqueId());
        sessions.beginPlaylist(player.getUniqueId(), state, source, reference);
        int generation = state.generation();
        player.sendMessage(languageService.text(player, Message.MUSIC_PLAYLIST_IMPORTING,
                NamedTextColor.YELLOW));
        screen.open(player, state);

        onlineSource.importPlaylist(source, reference)
                .whenComplete((result, error) -> runOnMainThread(() -> {
                    if (!isCurrent(player, state, generation)) {
                        return;
                    }
                    if (error != null) {
                        String message = rootMessage(error);
                        sessions.completePlaylist(state, generation, state.playlistName(),
                                List.of(), message, playbackStats);
                        player.sendMessage(languageService.text(player,
                                Message.MUSIC_PLAYLIST_IMPORT_FAILED,
                                NamedTextColor.RED, message));
                        screen.open(player, state);
                        return;
                    }
                    sessions.completePlaylist(state, generation, result.name(), result.tracks(),
                            null, playbackStats);
                    player.sendMessage(languageService.text(player,
                            Message.MUSIC_PLAYLIST_IMPORT_DONE, NamedTextColor.GREEN,
                            result.name(), result.tracks().size(), result.total()));
                    screen.open(player, state);
                }));
    }

    private void showSearchResult(Player player, MusicBrowserSessions.Session state,
                                  int generation, String keyword,
                                  List<MusicTrack> localMatches,
                                  MusicSearchResult<MusicTrack> onlineResult) {
        Map<String, MusicTrack> merged = new LinkedHashMap<>();
        localMatches.forEach(track -> merged.put(track.id(), track));
        onlineResult.items().forEach(track -> merged.putIfAbsent(track.id(), track));
        List<MusicTrack> tracks = MusicSearchRanker.rank(
                keyword, List.copyOf(merged.values()));
        String requestError = tracks.isEmpty() && !onlineResult.failures().isEmpty()
                ? onlineResult.failures().getFirst() : null;
        if (!sessions.completeSearch(state, generation,
                tracks, requestError, onlineResult.failures().size(),
                playbackStats)) {
            return;
        }
        sendPartialFailure(player, onlineResult.failures());
        if (tracks.isEmpty()) {
            player.sendMessage(languageService.rich(player, Message.MUSIC_NO_SEARCH_RESULTS_RICH,
                    NamedTextColor.RED,
                    RichArg.component("keyword", Component.text(keyword, NamedTextColor.YELLOW), keyword)));
        }
        screen.open(player, state);
    }

    private void sendPartialFailure(Player player, List<String> failures) {
        if (failures == null || failures.isEmpty()) {
            return;
        }
        player.sendMessage(languageService.text(player, Message.MUSIC_ONLINE_PARTIAL_FAILURE,
                NamedTextColor.YELLOW, failures.size()));
    }

    public void openCurrentPage(Player player, int page) {
        MusicBrowserSessions.Session state = screen.state(player).orElse(null);
        if (state == null) {
            showLibrary(player, page);
            return;
        }
        sessions.setPage(state, page);
        screen.open(player, state);
    }

    public void cycleSort(Player player) {
        MusicBrowserSessions.Session state = screen.state(player).orElse(null);
        if (state == null) {
            showLibrary(player, 0);
            return;
        }
        sessions.cycleSort(state, playbackStats);
        screen.open(player, state);
    }

    public void cycleSection(Player player) {
        MusicBrowserSessions.Session state = screen.state(player).orElse(null);
        if (state == null) {
            showLibrary(player, 0);
            return;
        }
        sessions.cycleSection(player.getUniqueId(), state, playbackStats);
        screen.open(player, state);
    }

    public List<MusicTrack> tracksInCurrentSection(UUID playerId, List<MusicTrack> tracks) {
        return sessions.tracksInSection(playerId, tracks);
    }

    public Integer getPlayerPage(UUID playerId) {
        MusicBrowserSessions.Session state = screen.state(playerId).orElse(null);
        return state == null ? null : state.page();
    }

    public int getTotalPages(UUID playerId) {
        MusicBrowserSessions.Session state = screen.state(playerId).orElse(null);
        int count = state == null
                ? sessions.tracksInSection(playerId, trackPool.tracks()).size()
                : state.tracks().size();
        return pageCount(count);
    }

    public void removePlayerData(UUID playerId) {
        screen.forget(playerId);
        sessions.removePlayer(playerId);
        requestLimiter.forget(playerId);
    }

    public void setJukeboxContext(UUID playerId, Location jukeboxLocation) {
        sessions.setJukeboxContext(playerId, jukeboxLocation);
    }

    public Location getJukeboxContext(UUID playerId) {
        return sessions.jukeboxContext(playerId);
    }

    public void prepareJukeboxSearch(UUID playerId) {
        sessions.prepareJukeboxSearch(playerId);
    }

    public void resumeJukeboxSearch(UUID playerId) {
        sessions.resumeJukeboxSearch(playerId);
    }

    private FloatingMenuDefinition buildCurrent(
            FloatingMenuContext<MusicBrowserSessions.Session> context) {
        Player player = context.player();
        MusicBrowserSessions.Session state = context.state();
        List<MusicTrack> items = state.tracks();
        int totalPages = pageCount(items.size());
        sessions.setPage(state, Math.max(0, Math.min(state.page(), totalPages - 1)));
        int start = state.page() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        MusicTrack selectedTrack = sessions.focusedTrackOnPage(state, start, end);

        String title = switch (state.view()) {
            case LIBRARY -> languageService.t(player,
                    state.section() == MusicBrowserSessions.Section.NBS
                            ? Message.MUSIC_MENU_NBS_TITLE : Message.MUSIC_MENU_TITLE,
                    state.page() + 1, totalPages);
            case ONLINE_SONGS -> languageService.t(player,
                    state.section() == MusicBrowserSessions.Section.NBS
                            ? Message.MUSIC_MENU_SEARCH_NBS_TITLE
                            : Message.MUSIC_MENU_SEARCH_TITLE,
                    truncate(state.keyword(), 24), state.page() + 1, totalPages);
            case PLAYLIST -> languageService.t(player, Message.MUSIC_MENU_PLAYLIST_TITLE,
                    truncate(state.playlistName(), 24), state.page() + 1, totalPages);
        };

        boolean jukeboxContext = sessions.hasJukeboxContext(player.getUniqueId());
        MusicBrowserActionHandler actions = java.util.Objects.requireNonNull(actionHandler,
                "music browser action handler");
        FloatingMenuLayouts.Panel browserPanel = FloatingMenuLayouts.panel("browser",
                FloatingMenuLayouts.verticalRegions(0.28,
                        FloatingMenuLayouts.region("context",
                                FloatingMenuLayouts.adaptiveRow(0.0)),
                        FloatingMenuLayouts.region("results",
                                FloatingMenuLayouts.adaptiveCurvedList(
                                        3, 6, 0.38, 0.18, 0.34)),
                        FloatingMenuLayouts.region("controls",
                                FloatingMenuLayouts.adaptiveCurvedGrid(
                                        4, 0.26, 0.18, 0.18))),
                "context", "results", "controls");
        FloatingMenuLayouts.Panel detailPanel = FloatingMenuLayouts.panel("track-detail",
                FloatingMenuLayouts.offset(
                        FloatingMenuLayouts.orient(
                                FloatingMenuLayouts.verticalRegions(0.20,
                                        FloatingMenuLayouts.region("detail-identity",
                                                FloatingMenuLayouts.adaptiveColumn(0.0)),
                                        FloatingMenuLayouts.region("detail-metadata",
                                                FloatingMenuLayouts.adaptiveColumn(0.0)),
                                        FloatingMenuLayouts.region("detail-actions",
                                                FloatingMenuLayouts.adaptiveColumn(0.0))),
                                9.0, -2.0),
                        0.0, 0.0, 0.16),
                "detail-identity", "detail-metadata", "detail-actions");
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("music-browser")
                .framing(FloatingMenuFraming.PANORAMIC)
                .stableAnchor()
                .layout(FloatingMenuLayouts.sidecar(browserPanel, detailPanel,
                        FloatingMenuLayouts.Side.RIGHT, 0.52));
        menu.information("view-context",
                        browserContext(player, state, title, items.size()))
                .region("context");
        for (int index = start; index < end; index++) {
            MusicTrack track = state.tracks().get(index);
            menu.control("track:" + track.id(), trackLabel(player, track))
                    .region("results")
                    .textWidth(FloatingMenuTextWidth.WIDE)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .selected(selectedTrack != null && track.id().equals(selectedTrack.id()))
                    .focus((p, handle, focused) -> {
                        if (focused && !track.id().equals(state.focusedTrackId())) {
                            sessions.focusTrack(state, track.id());
                            context.redraw();
                        }
                    })
                    .primary((p, handle) -> actions.track(p, track, false))
                    .secondary((p, handle) -> actions.track(p, track, true));
        }
        if (items.isEmpty()) {
            menu.information("state", stateText(player, state))
                    .region("results");
        } else if (selectedTrack != null) {
            menu.item("selected-track",
                            discFactory.createDisplayDisc(selectedTrack, false, player),
                            trackIdentityLabel(selectedTrack))
                    .region("detail-identity")
                    .textWidth(FloatingMenuTextWidth.WIDE)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .selected(true)
                    .primary((p, handle) -> actions.track(p, selectedTrack, false))
                    .secondary((p, handle) -> actions.track(p, selectedTrack, true));
            menu.information("selected-track-metadata",
                            trackMetadataLabel(player, selectedTrack))
                    .region("detail-metadata")
                    .textWidth(FloatingMenuTextWidth.WIDE)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT);
            menu.control("selected-track-actions",
                            trackActionLabel(player, jukeboxContext))
                    .region("detail-actions")
                    .textWidth(FloatingMenuTextWidth.WIDE)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .primary((p, handle) -> actions.track(p, selectedTrack, false))
                    .secondary((p, handle) -> actions.track(p, selectedTrack, true));
        }

        if (state.page() > 0) {
            menu.navigation("previous",
                            Component.text("‹ " + languageService.t(player,
                                    Message.MUSIC_PREV_PAGE), NamedTextColor.YELLOW))
                    .region("controls")
                    .primary((p, handle) -> actions.previousPage(p));
            menu.on(FloatingMenuInteraction.SCROLL_UP,
                    (p, handle, input) -> actions.previousPage(p));
        }
        menu.item("library", Material.CHEST,
                        Component.text(languageService.t(player, Message.MUSIC_LIBRARY_BUTTON),
                                NamedTextColor.GREEN))
                .region("controls")
                .primary((p, handle) -> actions.library(p));
        menu.item("search", Material.COMPASS,
                        Component.text(languageService.t(player, Message.MUSIC_SEARCH_BUTTON),
                                NamedTextColor.AQUA))
                .region("controls")
                .primary((p, handle) -> actions.search(p));
        menu.item("import-playlist", Material.WRITABLE_BOOK,
                        Component.text(languageService.t(player,
                                Message.MUSIC_PLAYLIST_IMPORT_BUTTON), NamedTextColor.BLUE))
                .region("controls")
                .primary((p, handle) -> actions.importPlaylist(p));
        menu.item("sort", Material.HOPPER, sortLabel(player, state.sort()))
                .region("controls")
                .primary((p, handle) -> actions.cycleSort(p));
        boolean nbsSection = state.section() == MusicBrowserSessions.Section.NBS;
        menu.item("section", nbsSection ? Material.NOTE_BLOCK : Material.JUKEBOX,
                        sectionLabel(player, state))
                .region("controls")
                .primary((p, handle) -> actions.cycleSection(p));
        menu.item("random", randomVisual(), randomLabel(player, jukeboxContext))
                .region("controls")
                .primary((p, handle) -> actions.random(p, false))
                .secondary((p, handle) -> actions.random(p, true));
        menu.navigation("back",
                        Component.text(languageService.t(player, Message.MUSIC_BACK),
                                NamedTextColor.RED))
                .region("controls")
                .primary((p, handle) -> actions.back(p));
        if (state.page() < totalPages - 1) {
            menu.navigation("next",
                            Component.text(languageService.t(player,
                                    Message.MUSIC_NEXT_PAGE) + " ›", NamedTextColor.YELLOW))
                    .region("controls")
                    .primary((p, handle) -> actions.nextPage(p));
            menu.on(FloatingMenuInteraction.SCROLL_DOWN,
                    (p, handle, input) -> actions.nextPage(p));
        }
        return menu.build();
    }

    private Component trackLabel(Player player, MusicTrack track) {
        Component label = Component.text(truncate(
                        track.details().title(), TRACK_TITLE_DISPLAY_LIMIT),
                NamedTextColor.WHITE);
        String artist = track.details().artist() != null
                ? track.details().artist() : track.details().originalAuthor();
        Component metadata = artist == null || artist.isBlank()
                ? Component.empty()
                : Component.text(truncate(artist, TRACK_ARTIST_DISPLAY_LIMIT),
                        NamedTextColor.DARK_GRAY);
        if (track.target() instanceof TrackTarget.Lx && cachedTrack.test(track)) {
            if (!Component.empty().equals(metadata)) {
                metadata = metadata.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
            }
            metadata = metadata.append(Component.text(languageService.t(player,
                    Message.MUSIC_CACHED), NamedTextColor.GREEN));
        }
        return Component.empty().equals(metadata)
                ? label : label.append(Component.newline()).append(metadata);
    }

    private Component sortLabel(Player player, MusicBrowserSort sort) {
        Message current = switch (sort) {
            case DEFAULT -> Message.MUSIC_SORT_DEFAULT;
            case MOST_PLAYED -> Message.MUSIC_SORT_MOST_PLAYED;
            case RECENTLY_PLAYED -> Message.MUSIC_SORT_RECENTLY_PLAYED;
        };
        return Component.text(languageService.t(player, Message.MUSIC_SORT_BUTTON),
                        NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, Message.MUSIC_SORT_CURRENT,
                        languageService.t(player, current)), NamedTextColor.GRAY));
    }

    private Component sectionLabel(Player player, MusicBrowserSessions.Session state) {
        boolean nbs = state.section() == MusicBrowserSessions.Section.NBS;
        return Component.text(languageService.t(player,
                        nbs ? Message.MUSIC_SECTION_NBS : Message.MUSIC_SECTION_ALL),
                nbs ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
    }

    private Component browserContext(Player player, MusicBrowserSessions.Session state,
                                     String title, int total) {
        Component label = Component.text(title, NamedTextColor.DARK_PURPLE)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                        Message.MUSIC_PAGE_TOTAL, total), NamedTextColor.GRAY));
        if (state.requestError() != null) {
            label = label.append(Component.newline()).append(Component.text(languageService.t(player,
                    Message.MUSIC_REQUEST_ERROR, truncate(state.requestError(), 80)), NamedTextColor.RED));
        } else if (state.partialFailures() > 0) {
            label = label.append(Component.newline()).append(Component.text(languageService.t(player,
                    Message.MUSIC_PARTIAL_RESULTS, state.partialFailures()), NamedTextColor.YELLOW));
        }
        return label;
    }

    private Component trackIdentityLabel(MusicTrack track) {
        TrackDetails details = track.details();
        Component identity = Component.text(truncate(details.title(), 64), NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD);
        String artist = details.artist() != null
                ? details.artist() : details.originalAuthor();
        return artist == null ? identity : identity.append(Component.newline())
                .append(Component.text(truncate(artist, 64), NamedTextColor.GRAY));
    }

    private Component trackMetadataLabel(Player player, MusicTrack track) {
        TrackDetails details = track.details();
        AudioProperties audio = details.audio();
        List<Component> lines = new ArrayList<>();
        if (details.artist() != null && details.originalAuthor() != null) {
            addDetailLine(lines, player, Message.MUSIC_ORIGINAL_AUTHOR,
                    details.originalAuthor());
        }
        addDetailLine(lines, player, Message.MUSIC_ALBUM, details.album());
        addDetailLine(lines, player, Message.MUSIC_DURATION,
                AudioPropertiesFormatter.duration(audio.duration()));
        addDetailLine(lines, player, Message.MUSIC_FORMAT, details.format());
        addDetailLine(lines, player, Message.MUSIC_SAMPLE_RATE,
                AudioPropertiesFormatter.sampleRate(audio.sampleRateHz()));
        addDetailLine(lines, player, Message.MUSIC_SIZE,
                AudioPropertiesFormatter.fileSize(audio.fileSizeBytes()));
        if (track.target() instanceof TrackTarget.Lx && cachedTrack.test(track)) {
            lines.add(Component.text(languageService.t(player, Message.MUSIC_CACHED),
                    NamedTextColor.GREEN));
        }
        return joinLines(lines);
    }

    private Component trackActionLabel(Player player, boolean jukeboxContext) {
        return Component.text(languageService.t(player, jukeboxContext
                        ? Message.MUSIC_BROWSER_LEFT_ADD_QUEUE
                        : Message.MUSIC_BROWSER_LEFT_TAKE_DISC), NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, jukeboxContext
                        ? Message.MUSIC_BROWSER_RIGHT_PLAY_NOW
                        : Message.MUSIC_BROWSER_RIGHT_PLAY_NEARBY), NamedTextColor.AQUA));
    }

    private void addDetailLine(List<Component> lines, Player player,
                               Message message, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(Component.text(languageService.t(player, message,
                    truncate(value, 72)), NamedTextColor.GRAY));
        }
    }

    private static Component joinLines(List<Component> lines) {
        Component result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) result = result.append(Component.newline());
            result = result.append(lines.get(index));
        }
        return result;
    }

    static int pageCount(int itemCount) {
        return Math.max(1, (Math.max(0, itemCount) + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    private Component stateText(Player player, MusicBrowserSessions.Session state) {
        if (state.loading()) {
            return Component.text(languageService.t(player, Message.MUSIC_LOADING_TITLE),
                            NamedTextColor.YELLOW)
                    .append(Component.newline())
                    .append(Component.text(languageService.t(player,
                            Message.MUSIC_LOADING_DESCRIPTION),
                            NamedTextColor.GRAY));
        }
        if (state.requestError() != null) {
            return Component.text(languageService.t(player, Message.MUSIC_REQUEST_ERROR,
                            truncate(state.requestError(), 80)), NamedTextColor.RED)
                    .append(Component.newline())
                    .append(Component.text(emptyText(player, state.view()), NamedTextColor.GRAY));
        }
        if (state.section() == MusicBrowserSessions.Section.NBS) {
            return Component.text(languageService.t(player, Message.MUSIC_EMPTY_NBS),
                            NamedTextColor.GRAY)
                    .append(Component.newline())
                    .append(Component.text(languageService.t(player,
                            Message.MUSIC_EMPTY_NBS_DESCRIPTION), NamedTextColor.DARK_GRAY));
        }
        Message title = switch (state.view()) {
            case LIBRARY -> Message.MUSIC_EMPTY_LIBRARY;
            case ONLINE_SONGS -> Message.MUSIC_EMPTY_SEARCH;
            case PLAYLIST -> Message.MUSIC_EMPTY_PLAYLIST;
        };
        return Component.text(languageService.t(player, title), NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text(emptyText(player, state.view()), NamedTextColor.DARK_GRAY));
    }

    private String emptyText(Player player, MusicBrowserSessions.View view) {
        Message message = switch (view) {
            case LIBRARY -> Message.MUSIC_EMPTY_LIBRARY_DESCRIPTION;
            case ONLINE_SONGS -> Message.MUSIC_EMPTY_SEARCH_DESCRIPTION;
            case PLAYLIST -> Message.MUSIC_EMPTY_PLAYLIST_DESCRIPTION;
        };
        return languageService.t(player, message);
    }

    private Component randomLabel(Player player, boolean jukeboxContext) {
        Message name = jukeboxContext ? Message.MUSIC_RANDOM_ADD : Message.MUSIC_RANDOM_DISC;
        return Component.text(languageService.t(player, name), NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD);
    }

    private ItemStack randomVisual() {
        return new ItemStack(Material.MUSIC_DISC_13);
    }

    private boolean isCurrent(Player player, MusicBrowserSessions.Session expectedState,
                              int generation) {
        return player.isOnline() && screen.flow(player)
                .filter(flow -> flow.state() == expectedState)
                .filter(flow -> expectedState.generation() == generation)
                .isPresent();
    }

    private MusicBrowserSessions.Session currentOrNew(UUID playerId) {
        return screen.state(playerId).orElseGet(MusicBrowserSessions.Session::new);
    }

    private void runOnMainThread(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Search keyword must not be blank");
        }
        String normalized = keyword.strip();
        if (normalized.length() > 256) {
            throw new IllegalArgumentException("Search keyword exceeds 256 characters");
        }
        return normalized;
    }

    private void sendKeywordError(Player player, String keyword) {
        Message message = keyword == null || keyword.isBlank()
                ? Message.MUSIC_SEARCH_KEYWORD_REQUIRED : Message.MUSIC_SEARCH_KEYWORD_TOO_LONG;
        player.sendMessage(languageService.text(player, message, NamedTextColor.RED, 256));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String truncate(String text, int maximum) {
        return truncateText(text, maximum);
    }

    private static String truncateText(String text, int maximum) {
        if (text.length() <= maximum) {
            return text;
        }
        int end = maximum - 3;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "...";
    }

}
