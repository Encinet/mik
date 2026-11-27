package org.encinet.mik.feature.world.heads;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.heads.HeadScenes.Section;
import org.encinet.mik.feature.world.heads.catalog.HeadCatalog;
import org.encinet.mik.feature.world.heads.catalog.HeadCatalogs;
import org.encinet.mik.feature.world.heads.catalog.HeadEntry;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/** Owns each player's navigation, outstanding reads and spatial tasks. */
final class HeadMenu implements AutoCloseable {
    static final int QUERY_LIMIT = 128;
    private final HeadCatalogs catalogs;
    private final HeadFavorites storage;
    private final HeadPlayers players;
    private final HeadWork worker;
    private final Executor main;
    private final LanguageService languages;
    private final Logger logger;
    private final Map<UUID, Session> sessions = new HashMap<>();

    HeadMenu(HeadCatalogs catalogs, HeadFavorites storage, HeadPlayers players, HeadWork worker, Executor main,
             LanguageService languages, Logger logger) {
        this.catalogs = catalogs;
        this.storage = storage;
        this.players = players;
        this.worker = worker;
        this.main = main;
        this.languages = languages;
        this.logger = logger;
    }

    void open(Player player, Section section, String query) {
        if (!player.hasPermission(HeadsModule.PERMISSION)) {
            player.sendMessage(languages.text(player, Message.HEADS_DENIED, NamedTextColor.RED));
            return;
        }
        Session existing = sessions.get(player.getUniqueId());
        if (existing != null) {
            if (section != null) existing.navigate(section, query);
            FloatingMenus.present(player, existing.scene());
            return;
        }
        var session = new Session(player, section == null ? Section.CATALOG : section, query);
        sessions.put(player.getUniqueId(), session);
        try {
            session.handle = FloatingMenus.open(player, session.scene());
            session.loadFavorites();
            session.reload(false);
        } catch (RuntimeException error) {
            session.close();
            throw error;
        }
    }

    @Override public void close() {
        var shutdown = new ShutdownSequence();
        for (Session session : List.copyOf(sessions.values())) shutdown.attempt("head library", session::close);
        shutdown.finish("Head library shutdown failed");
    }

    private static final class Browse {
        String query = "";
        final Map<HeadCatalog.Facet, Set<String>> facets = new EnumMap<>(HeadCatalog.Facet.class);
        HeadCatalog.Sort sort = HeadCatalog.Sort.RELEVANCE;
        boolean descending;
        int position;

        HeadCatalog.Filter filter() { return new HeadCatalog.Filter(query, facets, sort, descending); }
        int count() { return facets.values().stream().mapToInt(Set::size).sum(); }
    }

    private final class Session implements AutoCloseable {
        private final Player player;
        private final UUID playerId;
        private final HeadScenes scenes;
        private final Map<Section, Browse> navigation = new EnumMap<>(Section.class);
        private Section section;
        private FloatingMenuHandle handle;
        private MenuQuerySession input;
        private Filters filters;
        private Detail detail;
        private CompletableFuture<?> request;
        private List<HeadEntry> entries = List.of();
        private Map<String, HeadEntry> favorites = Map.of();
        private Message favoriteProblem = Message.HEADS_FAVORITES_LOADING;
        private Message notice = Message.HEADS_LOADING;
        private Revision loaded;
        private boolean favoritePending;
        private boolean retry;
        private boolean closed;

        Session(Player player, Section section, String query) {
            this.player = player;
            this.playerId = player.getUniqueId();
            this.scenes = new HeadScenes(languages, player);
            this.section = section;
            if (query != null) browse().query = query;
        }

        private Browse browse() { return navigation.computeIfAbsent(section, _ -> new Browse()); }

        void navigate(Section value, String query) {
            if (section == value && query == null) return;
            if (section != value && input != null) { input.close(); input = null; }
            section = value;
            if (query != null) browse().query = query;
            reload(query != null);
        }

        MenuScene scene() {
            if (input == null) input = FloatingMenus.queryInput(player, scenes.label(Message.HEADS_FIND, section == Section.PLAYERS ? "player" : "head"),
                    QUERY_LIMIT, value -> { browse().query = value; reload(true); }, this::redraw);
            var state = catalogs.state();
            var view = new HeadScenes.Directory(section, state.local() != null && state.local().size() > 0,
                    input.content(browse().query), entries, browse().position, notice, retry,
                    section == Section.CATALOG && state.stale(), browse().count(), favorites.keySet());
            var actions = new HeadScenes.DirectoryActions(value -> navigate(value, null), position -> { browse().position = position; redraw(); },
                    (_, owner) -> openFilters(owner), this::retry, this::openDetail, this::favorite);
            return scenes.directory(view, actions).refreshEvery(20, (_, _) -> refresh())
                    .lifecycle((_, owner, _, next, _) -> {
                        handle = owner;
                        if (next == FloatingMenuState.CLOSING || next == FloatingMenuState.CLOSED) dispose();
                    }).build();
        }

        private Revision revision() {
            List<UUID> roster = section == Section.PLAYERS || section == Section.FAVORITES ? players.roster(player) : List.of();
            return new Revision(catalogs.state(), player.hasPermission(HeadsModule.PERMISSION), roster);
        }

        private void refresh() {
            if (revision().equals(loaded)) return;
            reload(false);
            if (filters != null) filters.updateLabels();
            if (detail != null) detail.redraw();
        }

        private void retry() {
            if (section == Section.FAVORITES && favoriteProblem != null) loadFavorites();
            else reload(false);
        }

        private void reload(boolean resetPosition) {
            if (closed) return;
            if (request != null) request.cancel(false);
            request = null;
            entries = List.of();
            if (resetPosition) browse().position = 0;
            retry = false;
            loaded = revision();
            if (!loaded.permitted()) {
                notice = Message.HEADS_DENIED;
                redraw();
                return;
            }
            var state = loaded.state();
            String query = browse().query;
            switch (section) {
                case CATALOG, LOCAL -> {
                    var catalog = section == Section.CATALOG ? state.remote() : state.local();
                    if (catalog == null) {
                        notice = state.loading() ? Message.HEADS_LOADING : Message.HEADS_UNAVAILABLE;
                        redraw();
                    } else {
                        var filter = browse().filter();
                        read(worker.submit(() -> catalog.search(filter)), List.of());
                    }
                }
                case PLAYERS -> {
                    var online = players.online(player, query);
                    var result = players.find(player, query).thenApply(found -> appendPlayer(online, found));
                    read(result, online);
                }
                case FAVORITES -> {
                    if (favoriteProblem != null) {
                        notice = favoriteProblem;
                        retry = favoriteProblem == Message.HEADS_FAVORITES_FAILED;
                        redraw();
                    } else {
                        var saved = favorites.values().stream()
                                .filter(entry -> entry.playerId() == null || players.visible(player, entry.playerId())).toList();
                        read(worker.submit(() -> saved.stream().map(entry -> {
                                    var resolved = state.find(entry.id());
                                    return resolved == null ? entry : resolved;
                                }).filter(entry -> HeadCatalog.matches(entry.name(), query) || HeadCatalog.matches(entry.id(), query))
                                .sorted(Comparator.comparing(HeadEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(HeadEntry::id)).toList()), List.of());
                    }
                }
            }
        }

        private List<HeadEntry> appendPlayer(List<HeadEntry> online, HeadEntry entry) {
            if (entry == null) return online;
            var result = new ArrayList<HeadEntry>(online.size() + 1);
            result.add(entry);
            online.stream().filter(other -> !other.id().equals(entry.id())).forEach(result::add);
            return List.copyOf(result);
        }

        private void read(CompletableFuture<List<HeadEntry>> result, List<HeadEntry> whileLoading) {
            request = result;
            entries = whileLoading;
            notice = Message.MENU_SEARCH_LOADING;
            redraw();
            result.whenCompleteAsync((list, error) -> {
                if (!live() || request != result) return;
                request = null;
                if (error == null) {
                    entries = section == Section.PLAYERS ? list.stream().filter(head -> players.visible(player, head.playerId())).toList() : list;
                    notice = section == Section.FAVORITES && favorites.isEmpty() ? Message.HEADS_FAVORITES_EMPTY : null;
                } else {
                    notice = Message.MENU_SEARCH_FAILED;
                    retry = true;
                }
                redraw();
            }, main);
        }

        private void loadFavorites() {
            favoriteProblem = Message.HEADS_FAVORITES_LOADING;
            worker.submit(() -> storage.load(playerId)).whenCompleteAsync((saved, error) -> {
                if (!live()) return;
                if (error != null) {
                    logger.warning("Cannot load head favorites for " + playerId + ": " + error);
                    favoriteProblem = Message.HEADS_FAVORITES_FAILED;
                } else {
                    favorites = saved;
                    favoriteProblem = null;
                }
                favoritesChanged();
            }, main);
            if (section == Section.FAVORITES) reload(false);
        }

        private void favorite(FloatingMenuHandle owner, HeadEntry selected) {
            if (!authorized(owner) || favoritePending) return;
            if (favoriteProblem != null) {
                owner.feedback(scenes.label(favoriteProblem), FloatingMenuFeedbackKind.WARNING);
                if (favoriteProblem == Message.HEADS_FAVORITES_FAILED) loadFavorites();
                return;
            }
            boolean add = !favorites.containsKey(selected.id());
            HeadEntry entry = add ? current(selected) : selected;
            if (entry == null || (add && entry.playerId() != null && !players.visible(player, entry.playerId()))) {
                owner.feedback(scenes.label(Message.HEADS_GONE), FloatingMenuFeedbackKind.WARNING);
                return;
            }
            favoritePending = true;
            UUID id = playerId;
            worker.submit(() -> storage.set(id, entry, add)).whenCompleteAsync((change, error) -> {
                if (!live()) return;
                favoritePending = false;
                if (error != null) {
                    logger.warning("Cannot change head favorites for " + id + ": " + error);
                    owner.feedback(scenes.label(Message.HEADS_FAVORITES_FAILED), FloatingMenuFeedbackKind.ERROR);
                } else {
                    favorites = change.entries();
                    if (change.full()) owner.feedback(scenes.label(Message.HEADS_FAVORITES_FULL), FloatingMenuFeedbackKind.WARNING);
                    else owner.feedback(scenes.label(Message.HEADS_FAVORITE_SAVED, add ? "add" : "remove"), FloatingMenuFeedbackKind.SUCCESS);
                }
                favoritesChanged();
            }, main);
            if (detail != null) detail.redraw();
        }

        private void favoritesChanged() {
            if (section == Section.FAVORITES) reload(false);
            else redraw();
            if (detail != null) detail.redraw();
        }

        private HeadEntry current(HeadEntry selected) {
            UUID id = selected.playerId();
            return id == null ? catalogs.state().find(selected.id()) : players.current(player, id, selected);
        }

        private void openFilters(FloatingMenuHandle owner) {
            if (!authorized(owner)) return;
            var next = new Filters(browse(), section);
            try {
                next.handle = owner.openChild(next.render());
                filters = next;
            } catch (RuntimeException error) {
                next.close();
                throw error;
            }
        }

        private void openDetail(FloatingMenuHandle owner, HeadEntry selected) {
            if (!authorized(owner)) return;
            if (selected.playerId() != null && !players.visible(player, selected.playerId())) {
                owner.feedback(scenes.label(Message.HEADS_GONE), FloatingMenuFeedbackKind.WARNING);
                return;
            }
            var next = new Detail(selected);
            next.handle = owner.openChild(next.render());
            detail = next;
            next.loadProfile();
        }

        private boolean live() { return !closed && player.isOnline() && sessions.get(playerId) == this; }

        private boolean authorized(FloatingMenuHandle owner) {
            if (!live()) return false;
            if (player.hasPermission(HeadsModule.PERMISSION)) return true;
            owner.feedback(scenes.label(Message.HEADS_DENIED), FloatingMenuFeedbackKind.ERROR);
            return false;
        }

        private void redraw() {
            if (!closed && handle != null) handle.update(scene());
        }

        private void dispose() {
            if (closed) return;
            closed = true;
            sessions.remove(playerId, this);
            if (request != null) request.cancel(false);
            if (input != null) input.close();
        }

        @Override public void close() {
            var shutdown = new ShutdownSequence();
            if (filters != null) shutdown.attempt("head filters", filters.handle::close);
            if (detail != null) shutdown.attempt("head detail", detail.handle::close);
            if (handle != null) shutdown.attempt("head library", handle::close);
            shutdown.attempt("head search", this::dispose);
            shutdown.finish("Head menu session shutdown failed");
        }

        private final class Filters {
            private FloatingMenuHandle handle;
            private final Browse browse;
            private final Section section;
            private final MenuQuerySession input;
            private HeadCatalog.Facet facet = HeadCatalog.Facet.CATEGORY;
            private String query = "";
            private List<HeadCatalog.Label> labels;
            private int position;
            private boolean closed;

            Filters(Browse browse, Section section) {
                this.browse = browse;
                this.section = section;
                input = FloatingMenus.queryInput(player, scenes.label(Message.HEADS_FIND_FILTER), QUERY_LIMIT,
                        text -> { query = text; position = 0; updateLabels(); }, this::redraw);
                labels = matching();
            }

            MenuScene render() {
                var view = new HeadScenes.FilterView(input.content(query), facet, labels, position,
                        Set.copyOf(browse.facets.getOrDefault(facet, Set.of())), browse.count(), browse.sort, browse.descending);
                return scenes.filters(view, new HeadScenes.FilterActions(this::facet, next -> { position = next; redraw(); },
                        this::toggle, this::sort, () -> { browse.descending = !browse.descending; changed(); },
                        () -> { browse.facets.clear(); changed(); })).toBuilder()
                        .lifecycle((_, _, _, state, _) -> { if (state == FloatingMenuState.CLOSED) close(); }).build();
            }

            private List<HeadCatalog.Label> matching() {
                var state = catalogs.state();
                var catalog = section == Section.LOCAL ? state.local() : state.remote();
                if (catalog == null) return List.of();
                var values = catalog.labels(facet);
                return query.isEmpty() ? values : values.stream().filter(label -> HeadCatalog.matches(label.name(), query)
                        || HeadCatalog.matches(label.id(), query)).toList();
            }

            private void updateLabels() { labels = matching(); redraw(); }

            private void facet(HeadCatalog.Facet kind) {
                if (facet == kind) return;
                facet = kind;
                query = "";
                position = 0;
                updateLabels();
            }

            private void sort() {
                var sorts = HeadCatalog.Sort.values();
                browse.sort = sorts[(browse.sort.ordinal() + 1) % sorts.length];
                changed();
            }

            private void toggle(String id) {
                var selected = browse.facets.computeIfAbsent(facet, _ -> new HashSet<>());
                if (!selected.remove(id)) selected.add(id);
                changed();
            }

            private void changed() { reload(true); redraw(); }
            private void redraw() { if (!closed) handle.update(render()); }
            private void close() {
                if (closed) return;
                closed = true;
                if (filters == this) filters = null;
                input.close();
            }
        }

        private final class Detail {
            private FloatingMenuHandle handle;
            private final HeadEntry selected;
            private HeadEntry profile;
            private Message problem = Message.HEADS_GONE;
            private int amount = 1;
            private boolean closed;

            Detail(HeadEntry selected) {
                this.selected = selected;
                if (selected.playerId() != null) {
                    if (selected.texture() != null) profile = selected;
                    else problem = Message.MENU_SEARCH_LOADING;
                }
            }

            private void loadProfile() {
                if (selected.playerId() == null || profile != null) return;
                players.find(player, selected.id()).whenCompleteAsync((found, error) -> {
                    if (closed || !live()) return;
                    profile = found;
                    problem = error == null ? Message.HEADS_GONE : Message.MENU_SEARCH_FAILED;
                    redraw();
                }, main);
            }

            MenuScene render() {
                HeadEntry entry = available();
                boolean saved = favorites.containsKey(selected.id());
                Message favoriteStatus = favoritePending ? Message.MENU_INPUT_SAVING : favoriteProblem;
                var view = new HeadScenes.Detail(entry, catalogs.state().catalog(selected.id()),
                        entry == null ? selected.name() : entry.name(), amount, saved, problem, favoriteStatus);
                return scenes.detail(view, value -> { amount = value; redraw(); }, this::give,
                        (_, owner) -> favorite(owner, entry == null ? selected : entry)).toBuilder()
                        .lifecycle((_, _, _, state, _) -> {
                            if (state == FloatingMenuState.CLOSED) {
                                closed = true;
                                if (detail == this) detail = null;
                            }
                        }).build();
            }

            private HeadEntry available() {
                if (!player.hasPermission(HeadsModule.PERMISSION)) return null;
                if (selected.playerId() == null) return current(selected);
                return players.current(player, selected.playerId(), profile);
            }

            private void give(Player actor, FloatingMenuHandle owner) {
                if (!authorized(owner)) return;
                var entry = available();
                if (entry == null) {
                    owner.feedback(scenes.label(Message.HEADS_GONE), FloatingMenuFeedbackKind.WARNING);
                    redraw();
                } else if (HeadItems.give(actor, entry, amount)) {
                    owner.feedback(scenes.label(Message.HEADS_RECEIVED, amount), FloatingMenuFeedbackKind.SUCCESS);
                } else {
                    owner.feedback(scenes.label(Message.HEADS_INVENTORY_FULL), FloatingMenuFeedbackKind.WARNING);
                }
            }

            void redraw() { if (!closed) handle.update(render()); }
        }
    }

    private record Revision(HeadCatalogs.State state, boolean permitted, List<UUID> roster) { }
}
