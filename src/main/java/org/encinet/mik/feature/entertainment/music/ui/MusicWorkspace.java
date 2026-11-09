package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.Collection;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.application.*;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscFactory;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.online.*;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** Owns a player's workbench and input resources. Scenes project state; services enforce domain rules. */
public final class MusicWorkspace implements AutoCloseable {
    final JavaPlugin plugin;
    final LanguageService language;
    final MusicQueries queries;
    final PlaylistService playlists;
    final MusicPlaybackController devices;
    final MusicDeviceRegistry registry;
    final MusicDeviceTravel travel;
    final MusicGroupService groups;
    final Supplier<List<MusicTrack>> catalog;
    final LxSourceService sources;
    final OnlineMusicRequestLimiter limiter;
    final JukeboxSettingsStore settings;
    final MusicRooms rooms;
    final MusicDiscFactory discs;
    private final Map<UUID, FloatingMenuFlow<MusicWorkspaceState>> flows = new HashMap<>();
    private record Input(String field, MenuFormSession session) { }
    private static final UiKey INPUT_EDITOR = UiKey.named("music-input-editor");
    private static final UiKey SEARCH_EDITOR = UiKey.named("music-search-field");
    private final Map<UUID, Input> forms = new HashMap<>();
    private final Map<UUID, MenuQuerySession> searchInputs = new HashMap<>();
    final MusicLibraryScenes library = new MusicLibraryScenes(this);
    final MusicPlaylistPicker playlistPicker = new MusicPlaylistPicker(this);
    final MusicDeviceScenes outputs = new MusicDeviceScenes(this);
    final MusicRhythmScenes games = new MusicRhythmScenes(this);
    final MusicSoundControls sound = new MusicSoundControls(this);
    final MusicPlayerScenes transport = new MusicPlayerScenes(this);
    final MusicImportScenes imports = new MusicImportScenes(this);
    final MusicDeviceLocator locator;
    private final org.bukkit.scheduler.BukkitTask indicatorTask;
    private boolean closed;

    public MusicWorkspace(JavaPlugin plugin, LanguageService language, MusicQueries queries,
            PlaylistService playlists, MusicPlaybackController devices, MusicDeviceRegistry registry, MusicDeviceTravel travel,
            MusicGroupService groups, Supplier<List<MusicTrack>> catalog, LxSourceService sources,
            OnlineMusicRequestLimiter limiter, JukeboxSettingsStore settings,
            MusicRooms rooms, MusicDiscFactory discs) {
        this.plugin = plugin; this.language = language; this.queries = queries; this.playlists = playlists;
        this.devices = devices; this.registry = registry; this.groups = groups; this.catalog = catalog;
        this.travel = travel;
        this.sources = sources; this.limiter = limiter; this.settings = settings;
        this.rooms = rooms; this.discs = discs;
        locator = new MusicDeviceLocator(registry,language);
        indicatorTask = Bukkit.getScheduler().runTaskTimer(plugin,this::indicators,10,10);
        rooms.setReturnToRoom((player, id) -> { open(player); panel(player, new Room(id)); });
    }

    public void open(Player player) {
        if (closed) return;
        var previous = flows.get(player.getUniqueId());
        if (previous != null && (previous.handle().state() == FloatingMenuState.ACTIVE
                || previous.handle().state() == FloatingMenuState.OPENING)) { previous.redraw(); return; }
        MusicWorkspaceState state = previous == null ? new MusicWorkspaceState() : previous.state();
        if (state.panel() instanceof GroupEditor editor) editor.draft().pickingInWorld = false;
        closeForm(player.getUniqueId());
        closeSearch(player.getUniqueId());
        if (previous != null) previous.close();
        flows.put(player.getUniqueId(), FloatingMenuFlow.openRoot(player, state, context -> render(context.player(), context.state())));
    }

    public void at(Player player, Jukebox box) {
        var flow = flows.get(player.getUniqueId());
        var selection = flow != null && flow.state().panel() instanceof GroupEditor editor && editor.draft().pickingInWorld
                ? editor.draft() : null;
        registry.discover(box).whenComplete((device, error) -> main(() -> {
            if (!player.isOnline()) return;
            if (error != null) { error(player, error); return; }
            if (selection != null) {
                if (!owns(player,flow.state()) || !(flow.state().panel() instanceof GroupEditor editor)
                        || editor.draft() != selection || !selection.pickingInWorld) return;
                open(player);
                safe(player,() -> {
                    if (device.group() != null) { player.sendActionBar(label(player,"group-member-standalone")); return; }
                    registry.authorize(player,registry.playbackTarget(device.id()));
                    var group = selection.group == null ? null : registry.snapshot().groups().get(selection.group);
                    if (selection.group != null && group == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
                    int existing = group == null ? 0 : group.members().size();
                    if (!selection.members.contains(device.id()) && selection.members.size() + existing >= MusicGroupService.MAX_MEMBERS)
                        throw new MusicProblem(MusicProblem.Code.LIMIT);
                    selection.pickingInWorld = false;
                    var record = registry.bound(player.getUniqueId(),device.id()) ? CompletableFuture.completedFuture(null)
                            : registry.bind(player,device.id(),"");
                    record.whenComplete((_,failed) -> main(() -> {
                        if (!owns(player,flow.state()) || !(flow.state().panel() instanceof GroupEditor current) || current.draft() != selection) return;
                        open(player);
                        if (failed != null) error(player,failed);
                        else safe(player,() -> selection.pick(device.id(),existing));
                        redraw(player);
                    }));
                });
                return;
            }
            UUID target = device.group() == null ? device.id() : device.group();
            Runnable show = () -> {
                if (!player.isOnline()) return;
                open(player);
                selectTarget(player,target);
                showDevices(player,new DevicePanel(target,DeviceSection.QUEUE));
            };
            if (registry.bound(player.getUniqueId(),target) || !registry.mayBind(player,target)) show.run();
            else registry.bind(player,target,"").whenComplete((_,failed) -> main(() -> {
                if (failed != null) error(player,failed);
                else show.run();
            }));
        }));
    }

    public MusicWorkspaceState state(Player player) { return flows.get(player.getUniqueId()).state(); }
    boolean owns(Player player, MusicWorkspaceState state) {
        var flow = flows.get(player.getUniqueId());
        return !closed && player.isOnline() && flow != null && flow.state() == state;
    }
    boolean active(Player player, MusicWorkspaceState state) {
        return owns(player, state) && flows.get(player.getUniqueId()).handle().state() == FloatingMenuState.ACTIVE;
    }
    void selectTarget(Player player, UUID id) {
        var state = state(player);
        state.target(id);
    }
    void redraw(Player player) { var flow = flows.get(player.getUniqueId()); if (flow != null) flow.redraw(); }

    UiNode deviceLocation(Player player, UUID target) {
        return MusicWorkspaceComposition.deviceLocation(locator.position(player,target),locator.coordinates(player,target),visitControl(player,target));
    }
    UiNode visitControl(Player player, UUID target) {
        UiNode visitAction = null;
        if (registry.snapshot().devices().containsKey(target)) {
            if (travel.pending(player.getUniqueId())) visitAction = Ui.text(label(player,"teleporting")).key("visit").noWrap().build();
            else {
                var button = Ui.button(label(player,"visit-device")).key("visit").noWrap().primary((viewer,_) -> safe(viewer,() -> visit(viewer,target)));
                var failure = travel.failure(player,target);
                if (failure != null) button.disabled(problem(player,failure));
                visitAction = button.build();
            }
        }
        return visitAction;
    }

    private void visit(Player player, UUID device) {
        var state = state(player);
        var future = travel.visit(player,device);
        redraw(player);
        future.whenComplete((_,failed) -> main(() -> {
            if (!owns(player,state)) return;
            var flow = flows.get(player.getUniqueId());
            if (flow.handle().state() != FloatingMenuState.ACTIVE) return;
            if (failed == null) flow.close();
            else { if (!future.isCancelled()) error(player,failed); redraw(player); }
        }));
    }
    String text(Player player, String key) { return language.t(player, Message.MUSIC_SPACE_LABEL, key); }
    Component label(Player player, String key) { return raw(text(player, key)); }
    Component trackCount(Player player, int count) { return language.text(player, Message.MUSIC_SPACE_TRACK_COUNT, NamedTextColor.WHITE, count); }
    Component raw(String value) { return Component.text(value, NamedTextColor.WHITE); }
    /** Information ids can contain source paths; encode them without changing the domain identifier. */
    static UiElement info(String id, Component value) { return Ui.text(value).key(UiKey.item(id)).textWidth(180).build(); }
    UiElement button(Player player, String id, Component value, Runnable action) {
        return Ui.button(value).key(id).primary((_, _) -> safe(player, action)).build();
    }
    UiElement button(Player player, String key, Runnable action) { return button(player, key, label(player, key), action); }
    UiElement choice(Player player, String key, Component value, boolean selected, Runnable action) {
        return Ui.button(value).key(key).selected(selected).primary((_, _) -> safe(player, action)).build();
    }
    static Ui.LinearBuilder column(String key) { return Ui.column().key(key).gap(UiLength.ui(8)); }
    static Ui.ContainerBuilder tools(String key) { return Ui.layout(UiLayouts.wrap(UiLength.ui(6), UiLength.ui(6))).key(key); }
    UiNode panelHeading(Player player, Component title, UiNode... actions) {
        return MusicWorkspaceComposition.panelHeading(title, button(player, "close-panel", () -> panel(player, new NoPanel())), actions);
    }

    MenuScene render(Player player, MusicWorkspaceState state) {
        var builder = MenuScene.screen("music-workspace", label(player, "title")).stableAnchor().withoutTitle().viewport(MusicWorkspaceComposition.VIEWPORT)
                .refreshWhenChanged(20, viewer -> revision(viewer, state), (viewer, _) -> redraw(viewer))
                .lifecycle((viewer, _, previous, current, _) -> {
                    if (current == FloatingMenuState.SUSPENDED || current == FloatingMenuState.CLOSING
                            || current == FloatingMenuState.CLOSED) {
                        state.cancelMoves();
                        travel.cancel(viewer.getUniqueId());
                        closeForm(viewer.getUniqueId());
                        closeSearch(viewer.getUniqueId());
                        this.transport.release(viewer.getUniqueId());
                    } else if (current == FloatingMenuState.OPENING && previous == FloatingMenuState.SUSPENDED) redraw(viewer);
                });
        UiNode browser = !queries.ready() ? info("unavailable", label(player, "unavailable"))
                : state.page == WorkspacePage.DEVICES ? outputs.directory(player, state, null) : library.browser(player, state);
        UiNode auxiliary = null;
        if (queries.ready()) {
            try {
                var input = forms.get(player.getUniqueId());
                auxiliary = input != null && input.field() == null ? inputContent(input) : auxiliary(player, state);
            } catch (MusicProblem problem) {
                auxiliary = column("problem").child(panelHeading(player, problem(player, problem.code()))).build();
            }
        }
        if (auxiliary != null) auxiliary = MusicWorkspaceComposition.task(state.panelScope, auxiliary);
        UiNode windowTools = tools("window-tools")
                .child(Ui.button(UiIcons.CROSS, label(player, "close")).key("close").primary((_, handle) -> handle.close()).build()).build();
        locator.decorate(player,state,builder);
        UiNode content = MusicWorkspaceComposition.content(state.panel() instanceof GroupEditor ? null : library.navigation(player, state), browser, auxiliary,
                queries.ready() ? this.transport.content(player, state) : info("waiting", label(player, "unavailable")), windowTools);
        if (forms.containsKey(player.getUniqueId()) || searchInputs.containsKey(player.getUniqueId())) {
            boolean[] visible = {false, false};
            UiTree.visit(content, (_, node) -> {
                if (node.key().equals(INPUT_EDITOR)) visible[0] = true;
                if (node.key().equals(SEARCH_EDITOR)) visible[1] = true;
            });
            if (!visible[0]) closeForm(player.getUniqueId());
            if (!visible[1]) closeSearch(player.getUniqueId());
        }
        return builder.content(content).build();
    }

    private Object revision(Player player, MusicWorkspaceState state) {
        var statuses = new ArrayList<Object>();
        statuses.add(language.language(player));
        statuses.add(queries.revision());
        if (state.target() != null) {
            UUID id = state.target();
            statuses.add(Optional.ofNullable(registry.controlFailure(player, id)));
            try {
                var status = this.transport.sample(player, state);
                statuses.add(List.of(id, status.status(), Optional.ofNullable(status.entry()), Optional.ofNullable(status.generation()),
                        Optional.ofNullable(status.track()), status.seekable(), status.durationMillis() == 0 ? status.positionMillis() / 1000 : 0,
                        devices.outputFailures(id), registry.playbackTarget(id).members().stream().map(registry::online).toList()));
            }
            catch (MusicProblem ignored) { statuses.add(id); }
        }
        var targets = registry.targets(player).stream().sorted(Comparator.comparing(org.encinet.mik.feature.entertainment.music.jukebox.DeviceTarget::name)
                .thenComparing(org.encinet.mik.feature.entertainment.music.jukebox.DeviceTarget::id)).toList();
        statuses.add(targets.stream().map(org.encinet.mik.feature.entertainment.music.jukebox.DeviceTarget::id).toList());
        for (var target : targets)
            statuses.add(List.of(target.id(), Optional.ofNullable(registry.controlFailure(player, target.id())),
                    target.members().stream().map(device -> List.of(device.id(), registry.online(device),
                            MusicDeviceLocation.from(player.getLocation(),device))).toList()));
        if (state.panel() instanceof Room room && rooms.get(room.device()) != null)
            statuses.add(List.of(rooms.get(room.device()).members(), rooms.readiness(room.device())));
        if (state.panel() instanceof RoomPicker) statuses.add(rooms.nearby(player));
        return statuses;
    }

    private void indicators() {
        for (var entry : flows.entrySet()) {
            var flow = entry.getValue();
            if (flow.handle().state() != FloatingMenuState.ACTIVE) continue;
            var player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) locator.pulse(player,flow.state());
        }
    }

    private UiNode auxiliary(Player player, MusicWorkspaceState state) {
        Panel panel = state.panel();
        if (panel instanceof NoPanel) return null;
        return switch (panel) {
            case Outputs picker -> outputs.directory(player, state, picker.pending());
            case Track track -> library.track(player, state, track);
            case DevicePanel device -> outputs.details(player, state, device);
            case MemberOutput member -> outputs.memberOutput(player, state, member);
            case GroupEditor editor -> outputs.groupEditor(player, state, editor.draft());
            case PlaylistSettings list -> library.settings(player, state, list);
            case Import task -> imports.content(player, state, task);
            case RoomPicker _ -> games.picker(player, state);
            case Room room -> games.room(player, state, room);
            case Confirmation decision -> confirmation(player, state, decision);
            case NoPanel _ -> throw new IllegalStateException();
        };
    }

    void panel(Player player, Panel next) {
        closeForm(player.getUniqueId());
        var state = state(player);
        state.panel(next);
        redraw(player);
    }
    void returnToMembers(Player player, MusicWorkspaceState state, MemberOutput source) {
        if (!active(player,state) || state.panel() != source) return;
        closeForm(player.getUniqueId());
        if (state.returnToMembers(source)) redraw(player);
    }
    boolean editing(Player player) { return forms.containsKey(player.getUniqueId()); }
    void toggleDeviceIdentity(Player player, MusicWorkspaceState state, Panel owner, DeviceIdentityView view) {
        if (!active(player,state) || editing(player) || !state.toggleDeviceIdentity(owner,view)) return;
        redraw(player);
    }
    void showRooms(Player player) {
        closeForm(player.getUniqueId());
        closeSearch(player.getUniqueId());
        var room = rooms.current(player.getUniqueId());
        state(player).rooms(room == null ? null : room.device());
        redraw(player);
    }
    void showDevices(Player player) {
        var state = state(player);
        closeForm(player.getUniqueId());
        closeSearch(player.getUniqueId());
        state.devices();
        redraw(player);
    }
    void showDevices(Player player, Panel next) {
        closeForm(player.getUniqueId());
        closeSearch(player.getUniqueId());
        var state = state(player);
        state.devices();
        state.panel(next);
        redraw(player);
    }
    void browse(Player player, Collection collection) {
        closeForm(player.getUniqueId());
        closeSearch(player.getUniqueId());
        var state = state(player);
        state.searchGeneration++;
        state.tasks.remove(TaskKey.of(Work.SEARCH));
        state.browse(collection);
        redraw(player);
    }
    void browsePlaylist(Player player, UUID id, Source source) {
        closeForm(player.getUniqueId());
        closeSearch(player.getUniqueId());
        var state = state(player);
        state.searchGeneration++;
        state.tasks.remove(TaskKey.of(Work.SEARCH));
        state.browsePlaylist(id, source);
        redraw(player);
    }
    void input(Player player, MenuInputForm form) {
        input(player, null, form, form.fields().getFirst().key());
    }
    private void input(Player player, String field, MenuInputForm form, String focus) {
        closeForm(player.getUniqueId());
        var session = FloatingMenus.inlineInput(player, form, () -> redraw(player), () -> {
            forms.remove(player.getUniqueId());
            redraw(player);
        });
        forms.put(player.getUniqueId(), new Input(field, session));
        session.focus(focus);
    }
    UiNode field(Player player, String id, String key, String value, int maximum, Consumer<String> apply) {
        return form(player, id, inputForm(player, key, value, maximum, apply));
    }
    UiNode form(Player player, String id, MenuInputForm form) {
        var input = forms.get(player.getUniqueId());
        if (input != null && id.equals(input.field())) return column(id).child(inputContent(input)).build();
        var fields = Ui.form().key(id);
        for (var field : form.fields()) fields.field(field.key(), field.label(), Ui.textField(field.initial()).preview(field.preview())
                .key(field.key()).placeholder(language.text(player, Message.MENU_INPUT_EMPTY, NamedTextColor.AQUA))
                .maxLines(field.multiline() ? 4 : 1)
                .primary((viewer, _) -> safe(viewer, () -> input(viewer, id, form, field.key()))).build());
        return fields.build();
    }
    private static UiNode inputContent(Input input) {
        return Ui.column().key(INPUT_EDITOR).child(input.session().content()).build();
    }
    void input(Player player, String key, String initial, int maximum, Consumer<String> apply) {
        input(player, inputForm(player, key, initial, maximum, apply));
    }
    private MenuInputForm inputForm(Player player, String key, String initial, int maximum, Consumer<String> apply) {
        return MenuInputForm.builder(label(player, key))
                .field(MenuInputForm.Field.text("value", label(player, key), initial, maximum).multiline(key.equals("description")))
                .validate((viewer, values) -> {
                    String value = values.text("value").strip();
                    try {
                        if (Set.of("name", "alias", "playlist-id").contains(key)) Playlist.text(value, maximum);
                        return null;
                    } catch (IllegalArgumentException invalid) { return problem(viewer, MusicProblem.Code.INVALID); }
                })
                .cancel(label(player, "cancel")).submit(label(player, "apply"),
                        (viewer, values) -> safe(viewer, () -> apply.accept(values.text("value").strip()))).build();
    }
    private void closeForm(UUID player) { var input = forms.remove(player); if (input != null) input.session().close(); }

    UiNode searchInput(Player player, String query, Component placeholder) {
        var input = searchInputs.computeIfAbsent(player.getUniqueId(), _ -> FloatingMenus.queryInput(player,
                placeholder, 128, value -> {
                    safe(player, () -> filter(player, value));
                    redraw(player);
                }, () -> redraw(player)));
        var content = input.content(query);
        return Ui.column().key(SEARCH_EDITOR).sizing(content.sizing()).child(content).build();
    }

    private void closeSearch(UUID player) {
        var input = searchInputs.remove(player);
        if (input != null) input.close();
    }

    <T> void run(Player player, TaskKey key, Supplier<CompletableFuture<T>> operation, Consumer<T> applied) {
        var state = state(player);
        if (state.busy(key)) return;
        long ticket = state.begin(key);
        CompletableFuture<T> future;
        try { future = operation.get(); }
        catch (Exception error) { state.finish(key, ticket, failure(player, error)); redraw(player); return; }
        redraw(player);
        future.whenComplete((result, error) -> main(() -> {
            if (!owns(player, state)) return;
            if (!state.finish(key, ticket, error == null ? null : failure(player, error))) return;
            if (error == null) safe(player, () -> applied.accept(result));
            redraw(player);
        }));
    }
    List<UiNode> taskStatus(Player player, MusicWorkspaceState state, TaskKey key) {
        var task = state.tasks.get(key);
        if (task == null || !task.busy() && task.error() == null) return List.of();
        String progress = key.work() == Work.SEARCH ? "searching" : key.work() == Work.IMPORT
                && state.panel() instanceof Import importing && importing.preview() == null ? "fetching-playlist" : "saving";
        Component status = task.busy() ? label(player, progress) : problem(player, task.error());
        return List.of(info("task-status:" + key, status));
    }
    Component problem(Player player, MusicProblem.Code code) {
        return language.text(player, Message.MUSIC_SPACE_ERROR, NamedTextColor.YELLOW, code.name());
    }
    void safe(Player player, Runnable action) { try { action.run(); } catch (Exception error) { error(player, error); } }
    public void error(Player player, Throwable error) {
        Component value = problem(player, failure(player, error));
        var flow = flows.get(player.getUniqueId());
        if (flow != null && flow.handle().state() == FloatingMenuState.ACTIVE) flow.feedback(value, FloatingMenuFeedbackKind.WARNING);
        else player.sendMessage(value);
    }
    private MusicProblem.Code failure(Player player, Throwable error) {
        var flow = flows.get(player.getUniqueId());
        String context = flow == null ? "opening" : flow.state().browser.collection.source() + "/" + flow.state().panel().getClass().getSimpleName();
        return MusicProblem.report(plugin.getLogger(), "Music workspace failed for player " + player.getUniqueId() + " (" + context + ")", error);
    }

    Component targetSummary(Player player, UUID id) {
        if (id == null) return label(player, "choose-targets");
        Component name = raw(outputs.name(player, id));
        var group = registry.snapshot().groups().get(id);
        if (group != null) name = name.append(raw(" · "))
                .append(language.text(player, Message.MUSIC_SPACE_DEVICE_COUNT, NamedTextColor.AQUA, group.members().size()));
        return name;
    }
    void submit(Player player, List<MusicTrack> tracks, PlaybackProgram.Submission placement) {
        if (tracks.isEmpty()) return;
        var state = state(player);
        if (state.target() == null) { panel(player, new Outputs(new PendingPlay(tracks, placement))); return; }
        submit(player, new MusicSubmission(player.getUniqueId(), state.target(), tracks, placement));
    }
    void submit(Player player, MusicSubmission request) {
        var state = state(player);
        if (state.busy(new TaskKey(Work.PLAYBACK, request.target()))) return;
        state.lastSubmission = request;
        run(player, new TaskKey(Work.PLAYBACK, request.target()), () -> devices.submit(player, request), _ -> {});
    }
    void confirm(Player player, Decision decision) { panel(player, new Confirmation(decision, state(player).panel())); }
    private UiNode confirmation(Player player, MusicWorkspaceState state, Confirmation confirmation) {
        String key = switch (confirmation.decision()) {
            case DeletePlaylist _ -> "delete";
            case ClearRecents _ -> "clear-recents";
            case EndRoom _ -> "end-room";
        };
        var body = column("confirmation").child(panelHeading(player, label(player, key)));
        if (confirmation.decision() instanceof DeletePlaylist list) body.child(info("scope", raw(list.name())));
        if (confirmation.decision() instanceof EndRoom) body.child(info("scope", label(player, "room-end-confirmation")));
        return body.child(tools("decision").child(button(player, "apply", label(player, key), () -> decide(player, confirmation)))
                        .child(button(player, "cancel", () -> panel(player, confirmation.returnTo()))).build()).build();
    }
    private void decide(Player player, Confirmation confirmation) {
        var state = state(player);
        panel(player, confirmation.returnTo());
        switch (confirmation.decision()) {
            case DeletePlaylist list -> run(player, new TaskKey(Work.LIBRARY, list.id()),
                    () -> playlists.delete(player.getUniqueId(), list.id(), list.version()), _ -> {
                        if (list.id().equals(state.browser.collection.playlist())) browse(player, state.browser.parent);
                        if (state.panel() instanceof PlaylistSettings current && current.playlist().equals(list.id())) panel(player, new NoPanel());
                    });
            case ClearRecents _ -> run(player, new TaskKey(Work.LIBRARY, player.getUniqueId()),
                    () -> playlists.clearRecents(player.getUniqueId()), _ -> {});
            case EndRoom room -> { rooms.end(player, room.device()); panel(player, new NoPanel()); }
        }
    }

    public void search(Player player, String query) {
        open(player);
        var state = state(player);
        String clean = cleanQuery(query);
        if (!clean.isBlank()) acquire(player);
        closeForm(player.getUniqueId());
        closeSearch(player.getUniqueId());
        state.browse(Collection.of(Source.CATALOG));
        searchCatalog(player, state, clean);
    }

    /** Filtering the visible collection leaves the auxiliary task and chat owner in place. */
    private void filter(Player player, String query) {
        var state = state(player);
        String clean = cleanQuery(query);
        if (state.browser.query.equals(clean)) return;
        if (state.browser.collection.source() == Source.CATALOG) {
            if (!clean.isBlank()) acquire(player);
            searchCatalog(player, state, clean);
        } else state.browser.query(clean);
    }

    private static String cleanQuery(String query) {
        try { return query.isBlank() ? "" : Playlist.text(query, 128); }
        catch (IllegalArgumentException invalid) { throw new MusicProblem(MusicProblem.Code.INVALID); }
    }

    private void searchCatalog(Player player, MusicWorkspaceState state, String clean) {
        state.browser.query(clean);
        long generation = ++state.searchGeneration;
        state.searchFailures = 0;
        state.results = catalog.get().stream().filter(track -> track.matches(clean)).toList();
        var key = TaskKey.of(Work.SEARCH);
        state.tasks.remove(key);
        if (clean.isBlank()) { redraw(player); return; }
        run(player, key, () -> sources.searchMusic(clean, 1, 200), result -> {
            if (state.searchGeneration != generation || !state.browser.query.equals(clean)) return;
            var merged = new LinkedHashMap<String, MusicTrack>();
            state.results.forEach(track -> merged.put(track.id(), track));
            result.items().forEach(track -> merged.putIfAbsent(track.id(), track));
            state.results = MusicSearchRanker.rank(clean, List.copyOf(merged.values()));
            state.searchFailures = result.failures().size();
        });
    }
    void acquire(Player player) {
        if (!limiter.tryAcquire(player.getUniqueId(), OnlineMusicRequestLimiter.Operation.SEARCH).allowed())
            throw new MusicProblem(MusicProblem.Code.BUSY);
    }
    public void importPlaylist(Player player, String source, String reference) {
        open(player);
        browse(player, Collection.of(Source.OWNED));
        imports.load(player, source, reference);
    }
    public void playlist(Player player, UUID id) {
        open(player);
        var list = playlists.visible(player.getUniqueId(), id);
        browsePlaylist(player, id, list.author().equals(player.getUniqueId()) ? Source.OWNED : Source.PUBLIC);
    }
    public void quit(UUID player) {
        this.transport.release(player);
        closeForm(player);
        closeSearch(player);
        var flow = flows.remove(player); if (flow != null) flow.close();
        sound.forget(player);
        travel.cancel(player);
        limiter.forget(player);
    }
    void main(Runnable action) {
        if (closed) return;
        if (Bukkit.isPrimaryThread()) action.run();
        else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action);
    }
    @Override public void close() {
        closed = true;
        indicatorTask.cancel();
        sound.close();
        transport.close();
        List.copyOf(forms.keySet()).forEach(this::closeForm);
        List.copyOf(searchInputs.keySet()).forEach(this::closeSearch);
        List.copyOf(flows.values()).forEach(FloatingMenuFlow::close);
        flows.clear();
    }
}
