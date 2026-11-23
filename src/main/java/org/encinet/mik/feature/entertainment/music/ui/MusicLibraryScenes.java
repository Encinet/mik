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
        if (browser.collection.source() == Source.CATALOG)
            headingActions.child(w.button(player, "refresh", () -> w.search(player, browser.query)));
        if (browser.collection.source() == Source.OWNED) headingActions.child(playlistManagement(browser.collection.source(), key -> w.label(player, key),
                (_, _) -> w.safe(player, () -> create(player, List.of())),
                (_, _) -> w.safe(player, () -> w.panel(player,state, new Import(null, ImportPage.SOURCE)))));
        if (playlist != null) {
            UUID id = playlist.id();
            headingActions.child(w.button(player, "back", () -> w.browse(player, browser.parent)))
                    .child(w.button(player, "playlist-settings", () -> settings(player, state, id)));
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
        var savedTracks = tracks(player, state);
        var tracks = browser.collection.source() == Source.CATALOG ? savedTracks
                : savedTracks.stream().map(browser.saved::decorate).toList();
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
        if (!browser.selecting && browser.collection.source() == Source.RECENT && !tracks.isEmpty()) controls.child(w.button(player, "clear-recents", () -> w.confirm(player, UiKey.named("clear-recents"), new ClearRecents())));
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
                    track -> (_, _) -> w.window(player, MusicTrackTable.detailSource(track), window -> track(player, state, track, window)),
                    (songs, placement) -> w.safe(player, () -> w.submit(player, songs, placement)),
                    track -> w.playlistPicker.button(player,state,List.of(track),UiKey.item("save-track:" + track.id())))
                    .detailKeys(MusicTrackTable::detailSource)
                    .page(browser.page,index -> { if (browser.page(revision,index)) w.redraw(player); }).containing(browser.trackFocus);
            if (browser.collection.source() != Source.CATALOG) {
                var view = new SavedView(revision, savedTracks.stream().map(MusicTrack::id).toList());
                table.observing(new UiPageObservation(view, page -> w.refreshSaved(player, state, revision, page.slice(savedTracks))));
            }
            if (browser.selecting && browser.move == null) table.pager(MusicTrackTable.selectionControls(tracks,selected,key -> w.label(player,key),visible -> {
                if (browser.revision != revision || !browser.selecting || browser.move != null) return;
                visible.forEach(track -> browser.selected.add(track.id())); w.redraw(player);
            }));
            body.child(table.build());
        }
        if (browser.collection.source() == Source.CATALOG && browser.catalog != null) {
            long revision = browser.revision;
            var pages = tools("catalog-pages");
            if (!browser.earlier.isEmpty()) pages.child(Ui.button(UiIcons.CHEVRON_LEFT, Component.translatable("spectatorMenu.previous_page"))
                    .key("previous-catalog").primary((_, _) -> w.turnCatalog(player, revision, false)).build());
            if (!browser.later.isEmpty() || !browser.catalog.nextCursor().isEmpty()) pages.child(Ui.button(UiIcons.CHEVRON_RIGHT, Component.translatable("spectatorMenu.next_page"))
                    .key("next-catalog").primary((_, _) -> w.turnCatalog(player, revision, true)).build());
            var controlsPage = pages.build();
            if (!controlsPage.children().isEmpty()) body.child(controlsPage);
        }
        if (browser.move != null) {
            var end = moves.end(player, state, playlist);
            if (end != null) body.child(end);
        }
        body.children(w.taskStatus(player, state, TaskKey.of(Work.SEARCH)));
        if (browser.collection.source() == Source.CATALOG && browser.catalog != null && browser.catalog.partial())
            body.child(info("partial-search", w.label(player, "partial-results")));
        body.children(w.taskStatus(player, state, new TaskKey(Work.LIBRARY, playlist == null ? player.getUniqueId() : playlist.id())));
        return UiTemplates.reading(body.build(),UiKey.named(tracks.isEmpty() ? "empty" : "tracks"),UiLength.ui(8));
    }

    private record SavedView(long revision, List<String> ids) { }

    static UiNode playlistManagement(Source source, Function<String, Component> label,
                                     FloatingMenuCommand create, FloatingMenuCommand importPlaylist) {
        var actions = tools("playlist-management");
        if (source == Source.OWNED) actions
                .child(Ui.button(label.apply("new-playlist")).key("new-playlist").primary(create).build())
                .child(Ui.button(label.apply("import")).key("import").primary(importPlaylist).build());
        return actions.build();
    }

    List<MusicTrack> tracks(Player player, MusicWorkspaceState state) {
        if (state.browser.collection.source() == Source.CATALOG)
            return state.browser.catalog == null ? List.of() : state.browser.catalog.tracks();
        var snapshot = w.queries.snapshot(player.getUniqueId());
        List<MusicTrack> result = switch (state.browser.collection.source()) {
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

    private MenuPanel track(Player player, MusicWorkspaceState state, MusicTrack track, MenuWindowHandle<Void> window) {
        // A detail window holds the clicked song even if playback advances underneath it.
        return () -> {
            var owned = window.current();
            var identity = column("track-identity").child(Ui.heading(w.raw(track.details().title())).key("song")
                    .style(UiTheme.TITLE).summaryLines(3).textWidth(220)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT).build(),LinearSlot.fit(LinearSlot.Alignment.START));
            if (track.details().artist() != null) identity.child(Ui.text(w.raw(track.details().artist())).key("artist")
                    .summaryLines(2).textWidth(220).alignment(FloatingMenuDecoration.Alignment.LEFT).build(),LinearSlot.fit(LinearSlot.Alignment.START));
            var facts = new ArrayList<UiNode>();
            facts.add(UiTemplates.fact(UiKey.named("duration"),w.label(player,"duration"),w.raw(track.details().durationHint() == null
                    ? "—" : MusicTime.format(track.details().durationHint().toMillis()))));
            facts.add(UiTemplates.fact(UiKey.named("availability"),w.label(player,"availability"),w.label(player,MusicTrackTable.availability(track))));
            if (track.details().album() != null) facts.add(UiTemplates.fact(UiKey.named("album"),w.label(player,"album"),w.raw(track.details().album())));
            if (track.details().originalAuthor() != null && !track.details().originalAuthor().equals(track.details().artist()))
                facts.add(UiTemplates.fact(UiKey.named("original-author"),w.label(player,"original-author"),w.raw(track.details().originalAuthor())));
            var playback = MusicPlaybackActions.buttons(List.of(track), key -> w.label(player,key),
                    (songs,placement) -> { if (owned.getAsBoolean()) w.safe(player,() -> w.submit(player,songs,placement)); });
            Component output = state.target() == null ? w.label(player,"choose-targets")
                    : w.language.text(player,Message.MUSIC_SPACE_PLAYBACK_TARGET,NamedTextColor.WHITE,w.outputs.name(player,state.target()));
            var outputControl = Ui.button(output).key("output").summaryLines(1).textWidth(180)
                    .primary((_,handle) -> { if (owned.getAsBoolean()) handle.openPopover(UiKey.named("output"),redraw -> w.outputs.picker(player,state,redraw)); }).build();
            var primary = UiTemplates.actionRow(outputControl,playback);
            var actions = tools("track-actions");
            actions.child(Ui.button(w.label(player,"add-playlist")).key("save-details")
                    .primary((_,_) -> { if (owned.getAsBoolean()) window.openWindow(MenuWindowSpec.task(UiKey.named("save-destination")), context -> w.playlistPicker.panel(player,state,List.of(track),context.handle())); }).build());
            actions.child(w.button(player, "make-disc", () -> {
                if (!owned.getAsBoolean()) return;
                if (player.getInventory().firstEmpty() < 0) throw new MusicProblem(MusicProblem.Code.LIMIT);
                player.getInventory().addItem(w.discs.createPersistentDisc(track, player));
            }));
            var snapshot = w.queries.snapshot(player.getUniqueId());
            if (state.browser.collection.source() == Source.RECENT
                    && snapshot.recents().values().stream().anyMatch(mark -> mark.track().equals(track.id())))
                actions.child(w.button(player,"remove",() -> {
                    if (owned.getAsBoolean()) w.run(player,new TaskKey(Work.LIBRARY,player.getUniqueId()),
                            () -> w.playlists.forgetRecent(player.getUniqueId(),track.id()),_ -> {});
                }));
            if (state.target() != null) {
                var target = w.registry.target(state.target());
                target.members().stream().filter(device -> w.registry.local(player,device)).findFirst().ifPresent(device ->
                        actions.child(w.button(player,"select-room-track",() -> {
                            if (!owned.getAsBoolean()) return;
                            w.rooms.open(player,device.id()); w.rooms.select(player,device.id(),track); w.panel(player,state,new Room(device.id()));
                        })));
            }
            var feedback = column("track-feedback").children(w.taskStatus(player,state,new TaskKey(Work.LIBRARY,player.getUniqueId()))).build();
            return MenuScene.screen("music-track",w.label(player,"track-details")).content(UiTemplates.detail(
                    identity.build(),UiTemplates.facts(facts),primary,actions.build(),feedback)).build();
        };
    }
    private UiNode playbackActions(Player player, List<MusicTrack> tracks) {
        return MusicPlaybackActions.buttons(tracks, key -> w.label(player, key),
                (songs, placement) -> w.safe(player, () -> w.submit(player, songs, placement)));
    }

    void create(Player player, List<MusicTrack> tracks) {
        var form = MenuInputForm.builder(w.label(player,"new-playlist"))
                .field(MenuInputForm.Field.text("name",w.label(player,"name"),"",64))
                .validate((viewer,values) -> {
                    try { Playlist.text(values.text("name"),64); return null; }
                    catch (IllegalArgumentException invalid) { return w.problem(viewer,MusicProblem.Code.INVALID); }
                }).cancel(w.label(player,"cancel")).submitAsync(w.label(player,"apply"),(viewer,values,done) -> {
                    var origin = FloatingMenus.current(viewer).orElseThrow();
                    var open = openSavedPlaylist(viewer);
                    w.persist(viewer,new TaskKey(Work.LIBRARY,viewer.getUniqueId()),
                            () -> w.playlists.create(viewer.getUniqueId(),values.text("name"),tracks),
                            saved -> { if (origin instanceof MenuWindowHandle<?> window && window.focused()) open.accept(saved); },done);
                }).build();
        w.input(player,UiKey.named("new-playlist"),form,"name",false);
    }
    Consumer<Playlist> openSavedPlaylist(Player player) {
        var state = w.state(player);
        return openSavedPlaylist(state, id -> { if (w.active(player,state)) w.browsePlaylist(player, id, Source.OWNED); });
    }
    static Consumer<Playlist> openSavedPlaylist(MusicWorkspaceState state, Consumer<UUID> open) {
        long revision = state.browser.revision;
        Panel panel = state.panel();
        long window = state.windowGeneration;
        return list -> {
            if (state.windowGeneration == window && state.page == WorkspacePage.LIBRARY && state.browser.revision == revision && state.panel() == panel)
                open.accept(list.id());
        };
    }
    private void settings(Player player, MusicWorkspaceState state, UUID id) {
        w.window(player,UiKey.item("playlist-settings:" + id), window -> settingsPanel(player,state,id,window));
    }
    private MenuPanel settingsPanel(Player player, MusicWorkspaceState state, UUID id, MenuWindowHandle<Void> window) {
        return () -> {
            var owned = window.current();
            Playlist list;
            try { list = w.playlists.visible(player.getUniqueId(),id); }
            catch (MusicProblem unavailable) {
                return MenuScene.screen("music-playlist-settings",w.label(player,"playlist-settings"))
                        .content(info("unavailable",w.problem(player,unavailable.code()))).build();
            }
            boolean editable = list.author().equals(player.getUniqueId());
            var identity = column("playlist-identity").child(Ui.heading(w.raw(list.name())).key("name").style(UiTheme.TITLE)
                    .summaryLines(2).textWidth(220).alignment(FloatingMenuDecoration.Alignment.LEFT).build(),LinearSlot.fit(LinearSlot.Alignment.START));
            if (!list.description().isBlank()) identity.child(Ui.text(w.raw(list.description())).key("description")
                    .summaryLines(3).textWidth(220).alignment(FloatingMenuDecoration.Alignment.LEFT).build(),LinearSlot.fit(LinearSlot.Alignment.START));
            var facts = UiTemplates.facts(List.of(UiTemplates.fact(UiKey.named("visibility"),w.label(player,"visibility"),
                    w.label(player,list.published() ? "public" : "private"))));
            var actions = tools("playlist-actions");
            var key = new TaskKey(Work.LIBRARY,id);
            if (editable) {
                actions.child(w.button(player,"edit-playlist",w.language.text(player,Message.MUSIC_SPACE_EDIT_PLAYLIST,NamedTextColor.WHITE),() -> {
                    if (!owned.getAsBoolean() || state.busy(key)) return;
                    var form = MenuInputForm.builder(w.label(player,"playlist-settings"))
                            .field(MenuInputForm.Field.text("name",w.label(player,"name"),list.name(),64))
                            .field(MenuInputForm.Field.text("description",w.label(player,"description"),list.description(),512).multiline(true).inputMethod(MenuInputForm.InputMethod.DIALOG))
                            .validate((viewer,values) -> {
                                try { Playlist.text(values.text("name"),64); return null; }
                                catch (IllegalArgumentException invalid) { return w.problem(viewer,MusicProblem.Code.INVALID); }
                            }).cancel(w.label(player,"cancel")).submitAsync(w.label(player,"apply"),(_,values,done) -> {
                                w.persist(player,key,() -> w.playlists.update(player.getUniqueId(),id,list.version(),
                                        values.text("name").strip(),values.text("description").strip(),list.icon(),list.published()),_ -> {},done);
                            }).build();
                    w.input(player,window,form,"name");
                }));
                actions.child(w.button(player,list.published() ? "make-private" : "publish",() -> {
                    if (owned.getAsBoolean()) w.run(player,key,() -> w.playlists.update(player.getUniqueId(),id,list.version(),
                            list.name(),list.description(),list.icon(),!list.published()),_ -> {});
                }));
                actions.child(w.button(player,"delete",() -> {
                    if (owned.getAsBoolean()) w.confirm(player,UiKey.named("playlist-settings"),new DeletePlaylist(id,list.version(),list.name()));
                }));
            }
            boolean favorite = w.queries.snapshot(player.getUniqueId()).favorites().containsKey(MusicState.key(player.getUniqueId(),id));
            actions.child(w.choice(player,"favorite",w.label(player,"favorite"),favorite,() -> {
                if (owned.getAsBoolean()) w.run(player,key,() -> w.playlists.favorite(player.getUniqueId(),id,!favorite),_ -> {});
            })).child(w.button(player,"copy",() -> {
                if (!owned.getAsBoolean()) return;
                var form = MenuInputForm.builder(w.label(player,"copy"))
                        .field(MenuInputForm.Field.text("name",w.label(player,"name"),list.name(),64))
                        .validate((viewer,values) -> {
                            try { Playlist.text(values.text("name"),64); return null; }
                            catch (IllegalArgumentException invalid) { return w.problem(viewer,MusicProblem.Code.INVALID); }
                        }).cancel(w.label(player,"cancel")).submitAsync(w.label(player,"apply"),(_,values,done) -> {
                            var current = window.current();
                            var origin = FloatingMenus.current(player).orElseThrow();
                            var open = openSavedPlaylist(player);
                            w.persist(player,key,() -> w.playlists.copy(player.getUniqueId(),id,values.text("name")),
                                    saved -> { if (current.getAsBoolean() && origin instanceof MenuWindowHandle<?> editor && editor.focused()) open.accept(saved); },done);
                        }).build();
                w.input(player,window,form,"name");
            }));
            if (list.published()) actions.child(w.button(player,"share",() -> {
                if (owned.getAsBoolean()) player.sendMessage(w.raw("/music playlist " + id)
                        .clickEvent(ClickEvent.copyToClipboard("/music playlist " + id)));
            }));
            return MenuScene.screen("music-playlist-settings",w.label(player,"playlist-settings"))
                    .content(UiTemplates.detail(identity.build(),facts,null,actions.build(),column("playlist-feedback").children(w.taskStatus(player,state,key)).build())).build();
        };
    }
}
