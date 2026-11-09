package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.column;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.info;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.tools;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.Collection;
import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.feature.entertainment.music.storage.*;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** Collection browsing and playlist tasks. Opening a task never replaces the browser. */
final class MusicLibraryScenes {
    private final MusicWorkspace w;
    private final MusicPlaylistMoves moves;
    MusicLibraryScenes(MusicWorkspace workspace) { w = workspace; moves = new MusicPlaylistMoves(workspace); }

    UiNode navigation(Player player, MusicWorkspaceState state) {
        return Ui.layout(UiLayouts.contentColumn(UiKey.named("sources"),UiLength.ui(8))).key("music-navigation")
                .child(navigation(state.page == WorkspacePage.DEVICES ? null : state.browser.navigationSource(), key -> w.label(player, key),
                        source -> (_, _) -> w.safe(player, () -> w.browse(player, Collection.of(source)))))
                .child(w.choice(player, "devices", w.label(player, "devices"), state.page == WorkspacePage.DEVICES, () -> w.showDevices(player)))
                .child(w.button(player, "rhythm", () -> w.showRooms(player))).build();
    }
    static UiNode navigation(Source selected, Function<String, Component> label, Function<Source, FloatingMenuCommand> select) {
        var nav = column("sources").child(Ui.heading(label.apply("title")).key("title").summaryLines(1).build());
        for (Source source : List.of(Source.CATALOG, Source.RECENT, Source.OWNED, Source.PUBLIC, Source.FAVORITES)) {
            nav.child(Ui.button(label.apply(sourceKey(source))).key("source:" + source).summaryLines(1)
                    .selected(source == selected).role(UiRole.NAVIGATION).primary(select.apply(source)).build());
        }
        return nav.build();
    }
    private static String sourceKey(Source source) {
        return switch (source) {
            case CATALOG -> "catalog"; case RECENT -> "recent";
            case OWNED -> "my-playlists"; case PUBLIC -> "public-playlists"; case FAVORITES -> "favorites"; case PLAYLIST -> "playlist";
        };
    }
    private boolean lists(Source source) { return source == Source.OWNED || source == Source.PUBLIC || source == Source.FAVORITES; }

    UiNode browser(Player player, MusicWorkspaceState state) {
        var browser = state.browser;
        Playlist playlist = null;
        if (browser.collection.source() == Source.PLAYLIST) {
            try { playlist = w.playlists.visible(player.getUniqueId(), browser.collection.playlist()); }
            catch (MusicProblem unavailable) {
                return column("collection").child(info("missing", w.problem(player, unavailable.code())))
                        .child(w.button(player, "back", () -> w.browse(player, browser.parent))).build();
            }
        }
        Component title = playlist == null ? w.label(player, sourceKey(browser.collection.source())) : w.raw(playlist.name());
        var headingActions = tools("collection-actions");
        if (browser.collection.source() == Source.OWNED) headingActions.child(playlistManagement(browser.collection.source(), key -> w.label(player, key),
                (_, _) -> w.safe(player, () -> create(player, List.of())),
                (_, _) -> w.safe(player, () -> w.panel(player, new Import(null, ImportPage.SOURCE)))));
        if (playlist != null) {
            UUID id = playlist.id();
            headingActions.child(w.button(player, "back", () -> w.browse(player, browser.parent)))
                    .child(w.button(player, "playlist-settings", () -> w.panel(player, new PlaylistSettings(id))));
        }
        var actions = headingActions.build();
        var heading = UiTemplates.titleBar(Ui.heading(title).summaryLines(1).key("title").build(),
                actions.children().isEmpty() ? null : actions);
        UiNode description = playlist != null && !playlist.description().isBlank()
                ? Ui.text(w.raw(playlist.description())).summaryLines(2).key("description").build() : null;
        var body = MusicWorkspaceComposition.collection(heading, description, w.searchInput(player, browser.query,
                w.label(player, lists(browser.collection.source()) ? "search-playlists" : "search-tracks")
                        .color(NamedTextColor.AQUA)));
        if (lists(browser.collection.source())) return playlists(player, state, body);
        var tracks = tracks(player, state);
        var controls = tools("collection-actions");
        if (browser.move != null) controls.child(moves.controls(player, state, playlist));
        else if (browser.selecting) controls.child(selection(player,state,tracks,playlist));
        else if (!tracks.isEmpty()) {
            controls.child(playbackActions(player, browser.playbackTracks(tracks)))
                    .child(w.choice(player, "multi-select", w.label(player, "multi-select"), browser.selecting, () -> {
                        browser.selecting = true;
                        w.redraw(player);
                    }));
        }
        if (browser.move == null && playlist != null && playlist.author().equals(player.getUniqueId()) && w.playlists.canUndo(player.getUniqueId(), playlist.id())) {
            var list = playlist;
            controls.child(w.button(player, "undo", () -> w.run(player, new TaskKey(Work.LIBRARY, list.id()),
                    () -> w.playlists.undo(player.getUniqueId(), list.id()), _ -> {})));
        }
        if (!browser.selecting && browser.collection.source() == Source.RECENT && !tracks.isEmpty()) controls.child(w.button(player, "clear-recents", () -> w.confirm(player, new ClearRecents())));
        var collectionTools = controls.build();
        if (!collectionTools.children().isEmpty()) body.child(collectionTools);
        if (tracks.isEmpty()) body.child(info("empty", w.label(player, "empty")));
        else {
            long revision = browser.revision;
            var selected = Set.copyOf(browser.selected);
            MusicTableSelection<MusicTrack> interaction = browser.move != null ? moves.selection(player, state, playlist)
                    : browser.selecting ? MusicTableSelection.selecting(track -> selected.contains(track.id()),
                            track -> (_,_) -> {
                                if (browser.revision != revision || !browser.selecting || browser.move != null) return;
                                browser.toggle(track.id()); w.redraw(player);
                            }) : null;
            var table = MusicTrackTable.builder(tracks,interaction,key -> w.label(player,key),
                    track -> (_, _) -> w.panel(player, new Track(List.of(track))),
                    (songs, placement) -> w.safe(player, () -> w.submit(player, songs, placement)),
                    track -> w.playlistPicker.button(player,state,List.of(track),UiKey.item("save-track:" + track.id())))
                    .page(browser.page,index -> { if (browser.page(revision,index)) w.redraw(player); }).containing(browser.trackFocus);
            if (browser.selecting && browser.move == null) table.pager(MusicTrackTable.selectionControls(tracks,selected,key -> w.label(player,key),visible -> {
                if (browser.revision != revision || !browser.selecting || browser.move != null) return;
                visible.forEach(track -> browser.selected.add(track.id())); w.redraw(player);
            }));
            body.child(table.build());
        }
        if (browser.move != null) {
            var end = moves.end(player, state, playlist);
            if (end != null) body.child(end);
        }
        body.children(w.taskStatus(player, state, TaskKey.of(Work.SEARCH)));
        if (browser.collection.source() == Source.CATALOG && state.searchFailures > 0)
            body.child(info("partial-search", w.language.text(player, org.encinet.mik.shared.i18n.Message.MUSIC_PARTIAL_RESULTS,
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW, state.searchFailures)));
        body.children(w.taskStatus(player, state, new TaskKey(Work.LIBRARY, playlist == null ? player.getUniqueId() : playlist.id())));
        return UiTemplates.reading(body.build(),UiKey.named(tracks.isEmpty() ? "empty" : "tracks"),UiLength.ui(8));
    }

    static UiNode playlistManagement(Source source, Function<String, Component> label,
                                     FloatingMenuCommand create, FloatingMenuCommand importPlaylist) {
        var actions = tools("playlist-management");
        if (source == Source.OWNED) actions
                .child(Ui.button(label.apply("new-playlist")).key("new-playlist").primary(create).build())
                .child(Ui.button(label.apply("import")).key("import").primary(importPlaylist).build());
        return actions.build();
    }

    List<MusicTrack> tracks(Player player, MusicWorkspaceState state) {
        var snapshot = w.queries.snapshot(player.getUniqueId());
        List<MusicTrack> result = switch (state.browser.collection.source()) {
            case CATALOG -> state.browser.query.isBlank() ? w.catalog.get() : state.results;
            case RECENT -> snapshot.recents().values().stream().sorted(Comparator.comparingLong(MusicState.Recent::at).reversed())
                    .map(mark -> snapshot.tracks().get(mark.track())).filter(Objects::nonNull).map(StoredTrack::track).toList();
            case PLAYLIST -> w.playlists.visible(player.getUniqueId(), state.browser.collection.playlist()).entries().stream()
                    .map(entry -> snapshot.tracks().get(entry.trackId())).filter(Objects::nonNull).map(StoredTrack::track).toList();
            default -> List.of();
        };
        var unique = new LinkedHashMap<String, MusicTrack>();
        result.stream().filter(track -> state.browser.query.isBlank() || track.matches(state.browser.query)).forEach(track -> unique.putIfAbsent(track.id(), track));
        return List.copyOf(unique.values());
    }

    private UiNode playlists(Player player, MusicWorkspaceState state, Ui.LinearBuilder body) {
        var snapshot = w.queries.snapshot(player.getUniqueId());
        var lists = switch (state.browser.collection.source()) {
            case PUBLIC -> w.playlists.published();
            case FAVORITES -> snapshot.favorites().values().stream().map(mark -> snapshot.playlists().get(mark.playlist()))
                    .filter(Objects::nonNull).sorted(Comparator.comparing(Playlist::name).thenComparing(Playlist::id)).toList();
            default -> w.playlists.owned(player.getUniqueId());
        };
        lists = lists.stream().filter(list -> list.name().toLowerCase(Locale.ROOT).contains(state.browser.query.toLowerCase(Locale.ROOT))).toList();
        if (lists.isEmpty()) body.child(info("empty", w.label(player, "no-playlists")));
        else {
            long revision = state.browser.revision;
            body.child(MusicPlaylistTable.content(lists.stream()
                        .map(list -> new MusicPlaylistTable.Row(list, false, null)).toList(), key -> w.label(player, key), count -> w.trackCount(player, count),
                list -> (_, _) -> w.safe(player, () -> w.browse(player, new Collection(Source.PLAYLIST, list.id()))),
                    state.browser.page,page -> { if (state.browser.page(revision,page)) w.redraw(player); }));
        }
        return UiTemplates.reading(body.children(w.taskStatus(player,state,new TaskKey(Work.LIBRARY,player.getUniqueId()))).build(),
                UiKey.named(lists.isEmpty() ? "empty" : "playlists"),UiLength.ui(8));
    }

    private UiNode selection(Player player, MusicWorkspaceState state, List<MusicTrack> ordered, Playlist list) {
        var browser = state.browser;
        List<MusicTrack> chosen = browser.playbackTracks(ordered);
        var body = column("selection");
        var tools = tools("selection-tools")
                .child(info("selected-count", w.language.text(player, Message.MUSIC_SPACE_SELECTED_TRACKS,
                        NamedTextColor.AQUA, chosen.size())));
        if (chosen.size() < ordered.size()) tools.child(w.button(player, "select-all", () -> {
            ordered.forEach(track -> browser.selected.add(track.id()));
            w.redraw(player);
        }));
        if (!chosen.isEmpty()) tools.child(w.button(player, "clear-tracks", () -> {
            browser.selected.clear();
            w.redraw(player);
        }));
        tools.child(w.button(player, "cancel-selection", () -> {
            browser.selecting = false;
            browser.selected.clear();
            w.redraw(player);
        }));
        body.child(tools.build());
        if (chosen.isEmpty()) return body.build();
        var actions = tools("selection-actions").child(playbackActions(player, chosen))
                .child(w.playlistPicker.button(player, state, chosen, UiKey.named("save-selection")));
        if (list != null && list.author().equals(player.getUniqueId())) {
            Set<UUID> entries = list.entries().stream().filter(entry -> browser.selected.contains(entry.trackId())).map(Playlist.Entry::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            var key = new TaskKey(Work.LIBRARY, list.id());
            actions.child(w.button(player, "remove", () -> w.run(player, key,
                    () -> w.playlists.remove(player.getUniqueId(), list.id(), list.version(), entries), _ -> browser.selected.clear())));
            var move = moves.start(player, state, list, entries);
            if (move != null) actions.child(move);
        }
        return body.child(actions.build()).build();
    }

    UiNode track(Player player, MusicWorkspaceState state, Track selection) {
        var songs = selection.tracks();
        var body = column("track").child(w.panelHeading(player, w.label(player, "track-details")));
        for (var track : songs.stream().limit(2).toList()) body.child(info("track:" + track.id(), w.raw(track.details().title())).withSummaryLines(2));
        body.child(info("output", w.targetSummary(player, state.target())).withSummaryLines(1));
        var actions = tools("track-actions");
        actions.child(playbackActions(player, songs));
        actions.child(w.playlistPicker.button(player, state, songs, UiKey.named("save-details")));
        if (songs.size() == 1) {
            var track = songs.getFirst();
            var snapshot = w.queries.snapshot(player.getUniqueId());
            actions.child(w.button(player, "make-disc", () -> {
                if (player.getInventory().firstEmpty() < 0) throw new MusicProblem(MusicProblem.Code.LIMIT);
                player.getInventory().addItem(w.discs.createPersistentDisc(track, player));
            }));
            if (state.panel() instanceof Track && state.browser.collection.source() == Source.RECENT) {
                if (snapshot.recents().values().stream().anyMatch(mark -> mark.track().equals(track.id())))
                    actions.child(w.button(player, "remove", () -> w.run(player, new TaskKey(Work.LIBRARY, player.getUniqueId()),
                            () -> w.playlists.forgetRecent(player.getUniqueId(), track.id()), _ -> {})));
            }
            if (state.target() != null) {
                var target = w.registry.target(state.target());
                var local = target.members().stream().filter(device -> w.registry.local(player, device)).findFirst();
                local.ifPresent(device -> actions.child(w.button(player, "select-room-track", () -> {
                    w.rooms.open(player, device.id()); w.rooms.select(player, device.id(), track); w.panel(player, new Room(device.id()));
                })));
            }
        }
        return body.child(actions.build()).children(w.taskStatus(player, state, new TaskKey(Work.LIBRARY, player.getUniqueId()))).build();
    }
    private UiNode playbackActions(Player player, List<MusicTrack> tracks) {
        return MusicPlaybackActions.buttons(tracks, key -> w.label(player, key),
                (songs, placement) -> w.safe(player, () -> w.submit(player, songs, placement)));
    }

    void create(Player player, List<MusicTrack> tracks) {
        w.input(player, "name", "", 64, name -> w.run(player, new TaskKey(Work.LIBRARY, player.getUniqueId()),
                () -> w.playlists.create(player.getUniqueId(), name, tracks), openSavedPlaylist(player)));
    }
    Consumer<Playlist> openSavedPlaylist(Player player) {
        return openSavedPlaylist(w.state(player), id -> w.browsePlaylist(player, id, Source.OWNED));
    }
    static Consumer<Playlist> openSavedPlaylist(MusicWorkspaceState state, Consumer<UUID> open) {
        long revision = state.browser.revision;
        Panel panel = state.panel();
        return list -> {
            if (state.page == WorkspacePage.LIBRARY && state.browser.revision == revision && state.panel() == panel)
                open.accept(list.id());
        };
    }
    UiNode settings(Player player, MusicWorkspaceState state, PlaylistSettings settings) {
        var list = w.playlists.visible(player.getUniqueId(), settings.playlist());
        boolean editable = list.author().equals(player.getUniqueId());
        var body = column("playlist-settings").child(w.panelHeading(player, editable ? w.label(player, "playlist-settings") : w.raw(list.name())));
        var actions = tools("playlist-actions");
        var key = new TaskKey(Work.LIBRARY, list.id());
        if (editable) {
            body.child(w.form(player, "playlist-details:" + list.id(), MenuInputForm.builder(w.label(player, "playlist-settings"))
                    .field(MenuInputForm.Field.text("name", w.label(player, "name"), list.name(), 64))
                    .field(MenuInputForm.Field.text("description", w.label(player, "description"), list.description(), 512).multiline(true))
                    .validate((viewer, values) -> {
                        try { Playlist.text(values.text("name"), 64); return null; }
                        catch (IllegalArgumentException invalid) { return w.problem(viewer, MusicProblem.Code.INVALID); }
                    })
                    .cancel(w.label(player, "cancel")).submit(w.label(player, "apply"), (_, values) -> w.run(player, key,
                            () -> w.playlists.update(player.getUniqueId(), list.id(), list.version(), values.text("name").strip(),
                                    values.text("description").strip(), list.icon(), list.published()), _ -> {})).build()));
            actions.child(w.button(player, list.published() ? "make-private" : "publish", () -> w.run(player, key,
                    () -> w.playlists.update(player.getUniqueId(), list.id(), list.version(), list.name(), list.description(), list.icon(), !list.published()), _ -> {})));
            actions.child(w.button(player, "delete", () -> w.confirm(player, new DeletePlaylist(list.id(), list.version(), list.name()))));
        }
        boolean favorite = w.queries.snapshot(player.getUniqueId()).favorites().containsKey(MusicState.key(player.getUniqueId(), list.id()));
        actions.child(w.choice(player, "favorite", w.label(player, "favorite"), favorite,
                () -> w.run(player, key, () -> w.playlists.favorite(player.getUniqueId(), list.id(), !favorite), _ -> {})))
                .child(w.button(player, "copy", () -> w.input(player, "name", list.name(), 64, name -> w.run(player, key,
                        () -> w.playlists.copy(player.getUniqueId(), list.id(), name), openSavedPlaylist(player)))));
        if (list.published()) actions.child(w.button(player, "share", () -> player.sendMessage(w.raw("/music playlist " + list.id())
                .clickEvent(ClickEvent.copyToClipboard("/music playlist " + list.id())))));
        return body.child(info("visibility", w.label(player, list.published() ? "public" : "private")))
                .child(actions.build()).children(w.taskStatus(player, state, key)).build();
    }
}
