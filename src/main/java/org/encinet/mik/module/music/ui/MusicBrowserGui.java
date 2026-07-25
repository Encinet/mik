package org.encinet.mik.module.music.ui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.music.online.MusicSearchResult;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackPool;
import org.encinet.mik.module.music.online.MusicSearchService;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.disc.MusicDiscKeys;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Renders the music browser and publishes asynchronous search results into player sessions. */
public final class MusicBrowserGui {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int GUI_SIZE = 54;
    private static final int SEARCH_LIMIT = 200;

    private final JavaPlugin plugin;
    private final MusicLibrary musicLibrary;
    private final MusicTrackPool trackPool;
    private final MusicSearchService onlineSearch;
    private final Predicate<MusicTrack> cachedTrack;
    private final MusicPlaybackStats playbackStats;
    private final MusicDiscFactory discFactory;
    private final LanguageService languageService;
    private final MusicBrowserSessions sessions = new MusicBrowserSessions();

    public MusicBrowserGui(JavaPlugin plugin, MusicLibrary musicLibrary,
                           MusicTrackPool trackPool,
                           MusicSearchService onlineSearch, Predicate<MusicTrack> cachedTrack,
                           MusicPlaybackStats playbackStats,
                           MusicDiscFactory discFactory,
                           LanguageService languageService) {
        this.plugin = plugin;
        this.musicLibrary = musicLibrary;
        this.trackPool = trackPool;
        this.onlineSearch = onlineSearch;
        this.cachedTrack = cachedTrack;
        this.playbackStats = playbackStats;
        this.discFactory = discFactory;
        this.languageService = languageService;
    }

    public void openMusicInventory(Player player) {
        showLibrary(player, 0);
    }

    public void showLibrary(Player player, int page) {
        MusicBrowserSessions.Session state = sessions.showLibrary(
                player.getUniqueId(), trackPool.tracks(), page, playbackStats);
        openCurrent(player, state);
    }

    public void searchMusic(Player player, String keyword) {
        String normalized;
        try {
            normalized = normalizeKeyword(keyword);
        } catch (IllegalArgumentException exception) {
            sendKeywordError(player, keyword);
            return;
        }
        MusicBrowserSessions.Session state = sessions.beginSearch(
                player.getUniqueId(), normalized);
        int generation = state.generation();
        player.sendMessage(languageService.text(player, Message.MUSIC_ONLINE_SEARCHING,
                NamedTextColor.YELLOW, normalized));
        openCurrent(player, state);

        List<MusicTrack> localMatches = musicLibrary.tracks().stream()
                .filter(track -> MusicSearchRanker.matches(normalized, track)).toList();
        onlineSearch.searchMusic(normalized, 1, SEARCH_LIMIT)
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

    private void showSearchResult(Player player, MusicBrowserSessions.Session state,
                                  int generation, String keyword,
                                  List<MusicTrack> localMatches,
                                  MusicSearchResult<MusicTrack> onlineResult) {
        Map<String, MusicTrack> merged = new LinkedHashMap<>();
        localMatches.forEach(track -> merged.put(track.id(), track));
        onlineResult.items().forEach(track -> merged.putIfAbsent(track.id(), track));
        List<MusicTrack> tracks = MusicSearchRanker.rank(
                keyword, List.copyOf(merged.values()), cachedTrack);
        String requestError = tracks.isEmpty() && !onlineResult.failures().isEmpty()
                ? onlineResult.failures().getFirst() : null;
        if (!sessions.completeSearch(player.getUniqueId(), state, generation,
                state.activeInventory(), tracks, requestError, onlineResult.failures().size(),
                playbackStats)) {
            return;
        }
        sendPartialFailure(player, onlineResult.failures());
        if (tracks.isEmpty()) {
            player.sendMessage(languageService.rich(player, Message.MUSIC_NO_SEARCH_RESULTS_RICH,
                    NamedTextColor.RED,
                    RichArg.component("keyword", Component.text(keyword, NamedTextColor.YELLOW), keyword)));
        }
        openCurrent(player, state);
    }

    private void sendPartialFailure(Player player, List<String> failures) {
        if (failures == null || failures.isEmpty()) {
            return;
        }
        player.sendMessage(languageService.text(player, Message.MUSIC_ONLINE_PARTIAL_FAILURE,
                NamedTextColor.YELLOW, failures.size()));
    }

    public void openCurrentPage(Player player, int page) {
        MusicBrowserSessions.Session state = sessions.current(player.getUniqueId());
        if (state == null) {
            showLibrary(player, page);
            return;
        }
        sessions.setPage(state, page);
        openCurrent(player, state);
    }

    public void cycleSort(Player player) {
        MusicBrowserSessions.Session state = sessions.current(player.getUniqueId());
        if (state == null) {
            showLibrary(player, 0);
            return;
        }
        sessions.cycleSort(state, playbackStats);
        openCurrent(player, state);
    }

    public Integer getPlayerPage(UUID playerId) {
        MusicBrowserSessions.Session state = sessions.current(playerId);
        return state == null ? null : state.page();
    }

    public int getTotalPages(UUID playerId) {
        MusicBrowserSessions.Session state = sessions.current(playerId);
        int count = state == null ? trackPool.tracks().size() : state.tracks().size();
        return Math.max(1, (count + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    public MusicTrack trackAt(UUID playerId, Inventory inventory,
                              int rawSlot, ItemStack displayedItem) {
        if (rawSlot < 0 || rawSlot >= ITEMS_PER_PAGE) {
            return null;
        }
        String displayedTrackId = MusicDiscKeys.trackId(displayedItem);
        if (displayedTrackId == null) {
            return null;
        }
        MusicBrowserSessions.Session state = sessions.current(playerId);
        if (!isCurrentInventory(playerId, inventory)) {
            return null;
        }
        List<MusicTrack> tracks = state.tracks();
        int index = state.page() * ITEMS_PER_PAGE + rawSlot;
        if (index >= tracks.size()) {
            return null;
        }
        MusicTrack track = tracks.get(index);
        return track.id().equals(displayedTrackId) ? track : null;
    }

    public boolean isMusicInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof BrowserHolder;
    }

    public boolean isCurrentInventory(UUID playerId, Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof BrowserHolder holder)) {
            return false;
        }
        return holder.playerId.equals(playerId) && sessions.isCurrentInventory(
                playerId, inventory, holder.generation, holder.view, holder.page);
    }

    public void removePlayerData(UUID playerId) {
        sessions.removePlayer(playerId);
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

    public void closeBrowserInventory(UUID playerId, Inventory inventory) {
        sessions.closeInventory(playerId, inventory);
    }

    private void openCurrent(Player player, MusicBrowserSessions.Session state) {
        List<MusicTrack> items = state.tracks();
        int totalPages = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        sessions.setPage(state, Math.max(0, Math.min(state.page(), totalPages - 1)));

        String title = switch (state.view()) {
            case LIBRARY -> languageService.t(player, Message.MUSIC_MENU_TITLE,
                    state.page() + 1, totalPages);
            case ONLINE_SONGS -> languageService.t(player, Message.MUSIC_MENU_SEARCH_TITLE,
                    truncate(state.keyword(), 24), state.page() + 1, totalPages);
        };

        BrowserHolder holder = new BrowserHolder(
                player.getUniqueId(), state.generation(), state.view(), state.page());
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE,
                Component.text(title, NamedTextColor.DARK_PURPLE));
        holder.attach(inventory);

        boolean jukeboxContext = sessions.hasJukeboxContext(player.getUniqueId());
        int start = state.page() * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start,
                    createTrackItem(player, state.tracks().get(index), jukeboxContext));
        }
        if (items.isEmpty()) {
            inventory.setItem(22, createStateItem(player, state));
        }

        if (state.page() > 0) {
            inventory.setItem(45, button(player, Material.ARROW,
                    Message.MUSIC_PREV_PAGE, Message.MUSIC_PREV_PAGE_LORE, NamedTextColor.YELLOW));
        }
        inventory.setItem(46, button(player, Material.CHEST,
                Message.MUSIC_LIBRARY_BUTTON, Message.MUSIC_LIBRARY_BUTTON_LORE, NamedTextColor.GREEN));
        inventory.setItem(47, createSearchButton(player));
        inventory.setItem(48, createSortButton(player, state.sort()));
        inventory.setItem(49, createPageInfo(player, state, state.page() + 1, totalPages,
                items.size()));
        inventory.setItem(50, createRandomDiscButton(player, jukeboxContext));
        inventory.setItem(51, createHelpButton(player));
        if (jukeboxContext) {
            inventory.setItem(52, button(player, Material.ARROW,
                    Message.MUSIC_BACK, Message.MUSIC_BACK_LORE, NamedTextColor.RED));
        }
        if (state.page() < totalPages - 1) {
            inventory.setItem(53, button(player, Material.ARROW,
                    Message.MUSIC_NEXT_PAGE, Message.MUSIC_NEXT_PAGE_LORE, NamedTextColor.YELLOW));
        }
        sessions.attachInventory(state, inventory);
        player.openInventory(inventory);
    }

    private ItemStack createTrackItem(Player player, MusicTrack track, boolean jukeboxContext) {
        ItemStack disc = discFactory.createDisplayDisc(track, !jukeboxContext, player);
        ItemMeta meta = disc.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (track.target() instanceof TrackTarget.Lx && cachedTrack.test(track)) {
            lore.addFirst(plain(Component.text(languageService.t(player, Message.MUSIC_CACHED),
                    NamedTextColor.GREEN)));
            lore.add(1, Component.empty());
        }
        MusicPlaybackStats.TrackStats stats = playbackStats.stats(track);
        if (stats.playCount() > 0) {
            lore.addFirst(plain(Component.text(languageService.t(player,
                    Message.MUSIC_PLAY_STATS, stats.playCount(),
                    formatLastPlayed(player, stats.lastPlayedAt())), NamedTextColor.GOLD)));
            lore.add(1, Component.empty());
        }
        if (jukeboxContext) {
            lore.add(Component.empty());
            lore.add(plain(Component.text(languageService.t(player,
                    Message.MUSIC_DISC_ADD_TO_QUEUE_LORE), NamedTextColor.GREEN)));
        }
        meta.lore(lore);
        disc.setItemMeta(meta);
        return disc;
    }

    private ItemStack createSortButton(Player player, MusicBrowserSort sort) {
        Message current = switch (sort) {
            case DEFAULT -> Message.MUSIC_SORT_DEFAULT;
            case MOST_PLAYED -> Message.MUSIC_SORT_MOST_PLAYED;
            case RECENTLY_PLAYED -> Message.MUSIC_SORT_RECENTLY_PLAYED;
        };
        return item(Material.HOPPER,
                plain(Component.text(languageService.t(player, Message.MUSIC_SORT_BUTTON),
                        NamedTextColor.YELLOW)),
                List.of(
                        plain(Component.text(languageService.t(player,
                                Message.MUSIC_SORT_CURRENT,
                                languageService.t(player, current)), NamedTextColor.GRAY)),
                        plain(Component.text(languageService.t(player,
                                Message.MUSIC_SORT_BUTTON_LORE), NamedTextColor.AQUA))));
    }

    private String formatLastPlayed(Player player, Instant lastPlayedAt) {
        if (lastPlayedAt == null) {
            return languageService.t(player, Message.MUSIC_LAST_PLAYED_UNKNOWN);
        }
        long seconds = Math.max(0, Duration.between(lastPlayedAt, Instant.now()).toSeconds());
        if (seconds < 60) {
            return languageService.t(player, Message.MUSIC_LAST_PLAYED_JUST_NOW);
        }
        if (seconds < 3_600) {
            return languageService.t(player, Message.MUSIC_LAST_PLAYED_MINUTES, seconds / 60);
        }
        if (seconds < 86_400) {
            return languageService.t(player, Message.MUSIC_LAST_PLAYED_HOURS, seconds / 3_600);
        }
        return languageService.t(player, Message.MUSIC_LAST_PLAYED_DAYS, seconds / 86_400);
    }

    private ItemStack createPageInfo(Player player, MusicBrowserSessions.Session state,
                                     int current, int pages, int total) {
        List<Component> lore = new ArrayList<>();
        lore.add(plain(Component.text(languageService.t(player, Message.MUSIC_PAGE_TOTAL, total),
                NamedTextColor.GRAY)));
        if (state.requestError() != null) {
            lore.add(plain(Component.text(languageService.t(player, Message.MUSIC_REQUEST_ERROR,
                    truncate(state.requestError(), 120)), NamedTextColor.RED)));
        } else if (state.partialFailures() > 0) {
            lore.add(plain(Component.text(languageService.t(player, Message.MUSIC_PARTIAL_RESULTS,
                    state.partialFailures()), NamedTextColor.YELLOW)));
        }
        return item(Material.PAPER,
                plain(Component.text(languageService.t(player, Message.MUSIC_PAGE_INFO, current, pages),
                        NamedTextColor.GOLD)),
                lore);
    }

    private ItemStack createStateItem(Player player, MusicBrowserSessions.Session state) {
        if (state.loading()) {
            return item(Material.CLOCK,
                    plain(Component.text(languageService.t(player, Message.MUSIC_LOADING_TITLE),
                            NamedTextColor.YELLOW)),
                    List.of(plain(Component.text(languageService.t(player, Message.MUSIC_LOADING_LORE),
                            NamedTextColor.GRAY))));
        }
        if (state.requestError() != null) {
            return item(Material.BARRIER,
                    plain(Component.text(languageService.t(player, Message.MUSIC_REQUEST_ERROR,
                            truncate(state.requestError(), 80)), NamedTextColor.RED)),
                    List.of(plain(Component.text(emptyLore(player, state.view()), NamedTextColor.GRAY))));
        }
        Message title = switch (state.view()) {
            case LIBRARY -> Message.MUSIC_EMPTY_LIBRARY;
            case ONLINE_SONGS -> Message.MUSIC_EMPTY_SEARCH;
        };
        return item(Material.GRAY_DYE,
                plain(Component.text(languageService.t(player, title), NamedTextColor.GRAY)),
                List.of(plain(Component.text(emptyLore(player, state.view()), NamedTextColor.DARK_GRAY))));
    }

    private String emptyLore(Player player, MusicBrowserSessions.View view) {
        Message lore = switch (view) {
            case LIBRARY -> Message.MUSIC_EMPTY_LIBRARY_LORE;
            case ONLINE_SONGS -> Message.MUSIC_EMPTY_SEARCH_LORE;
        };
        return languageService.t(player, lore);
    }

    private ItemStack createSearchButton(Player player) {
        String command = "/music search <keyword>";
        return item(Material.COMPASS,
                plain(Component.text(languageService.t(player, Message.MUSIC_SEARCH_BUTTON),
                        NamedTextColor.AQUA)),
                List.of(plain(Component.text(languageService.t(player,
                        Message.MUSIC_SEARCH_LORE_COMMAND, command), NamedTextColor.GRAY))));
    }

    private ItemStack createRandomDiscButton(Player player, boolean jukeboxContext) {
        Message name = jukeboxContext ? Message.MUSIC_RANDOM_ADD : Message.MUSIC_RANDOM_DISC;
        Message lore = jukeboxContext ? Message.MUSIC_RANDOM_ADD_LORE : Message.MUSIC_RANDOM_DISC_LEFT;
        List<Component> loreLines = new ArrayList<>();
        loreLines.add(plain(Component.text(languageService.t(player, lore), NamedTextColor.GRAY)));
        if (!jukeboxContext) {
            loreLines.add(plain(Component.text(languageService.t(player,
                    Message.MUSIC_RANDOM_DISC_RIGHT), NamedTextColor.GRAY)));
        }
        ItemStack item = item(Material.MUSIC_DISC_13,
                plain(Component.text(languageService.t(player, name), NamedTextColor.LIGHT_PURPLE)
                        .decorate(TextDecoration.BOLD)),
                loreLines);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.JUKEBOX_PLAYABLE).build());
        return item;
    }

    private ItemStack createHelpButton(Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(plain(Component.text("/music", NamedTextColor.AQUA)));
        lore.add(plain(Component.text("/music search <keyword>", NamedTextColor.AQUA)));
        lore.add(plain(Component.text("/music page <page>", NamedTextColor.AQUA)));
        lore.add(plain(Component.text("/music random", NamedTextColor.AQUA)));
        lore.add(plain(Component.text("/music randomplay", NamedTextColor.AQUA)));
        if (player.hasPermission("group." + Mik.GROUP_MANAGER)) {
            lore.add(plain(Component.text("/music reload", NamedTextColor.AQUA)));
            lore.add(plain(Component.text("/music sources", NamedTextColor.AQUA)));
            lore.add(plain(Component.text("/music sources import <url>", NamedTextColor.AQUA)));
            lore.add(plain(Component.text("/music sources update", NamedTextColor.AQUA)));
            lore.add(plain(Component.text("/music sources remove <id>", NamedTextColor.AQUA)));
            lore.add(plain(Component.text("/music cache [clear]", NamedTextColor.AQUA)));
        }
        return item(Material.BOOK,
                plain(Component.text(languageService.t(player, Message.MUSIC_HELP_TITLE),
                        NamedTextColor.GOLD).decorate(TextDecoration.BOLD)), lore);
    }

    private ItemStack button(Player player, Material material, Message name,
                             Message lore, NamedTextColor color) {
        return item(material, plain(Component.text(languageService.t(player, name), color)),
                List.of(plain(Component.text(languageService.t(player, lore), NamedTextColor.GRAY))));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCurrent(Player player, MusicBrowserSessions.Session expectedState,
                              int generation) {
        return player.isOnline() && sessions.isCurrent(player.getUniqueId(), expectedState,
                generation, player.getOpenInventory().getTopInventory());
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

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static final class BrowserHolder implements InventoryHolder {
        private final UUID playerId;
        private final int generation;
        private final MusicBrowserSessions.View view;
        private final int page;
        private Inventory inventory;

        private BrowserHolder(UUID playerId, int generation,
                              MusicBrowserSessions.View view, int page) {
            this.playerId = playerId;
            this.generation = generation;
            this.view = view;
            this.page = page;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
