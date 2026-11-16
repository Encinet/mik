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
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;
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
    private record TaskView(UUID player, MenuWindowHandle<Void> handle, MusicWorkspaceState owner) { }
    private final Map<MusicWorkspaceState,TaskView> taskViews = new IdentityHashMap<>();
    private record WorldPick(MusicWorkspaceState state, GroupDraft draft, MenuInputLease lease) { }
    private final Map<UUID,WorldPick> worldPicks = new HashMap<>();
    private final Map<UUID,UUID> deviceOpenings = new HashMap<>();
    private static final UiKey SEARCH_EDITOR = UiKey.named("music-search-field");
    private final Map<UUID, MenuQuerySession> searchInputs = new HashMap<>();
    final MusicLibraryScenes library = new MusicLibraryScenes(this);
    final MusicPlaylistPicker playlistPicker = new MusicPlaylistPicker(this);
    final MusicDeviceScenes outputs = new MusicDeviceScenes(this);
    final MusicRhythmScenes games = new MusicRhythmScenes(this);
    final MusicSoundControls sound = new MusicSoundControls(this);
    final MusicPlayerScenes transport = new MusicPlayerScenes(this);
    final MusicImportScenes imports = new MusicImportScenes(this);
    final MusicDeviceLocator locator;
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
        rooms.setReturnToRoom((player, id) -> { open(player); panel(player, new Room(id)); });
    }

    public void open(Player player) {
        if (closed) return;
        deviceOpenings.remove(player.getUniqueId());
        var pick = worldPicks.remove(player.getUniqueId());
        if (pick != null) { pick.draft().pickingInWorld = false; pick.lease().close(); }
        var previous = flows.get(player.getUniqueId());
        MusicWorkspaceState state = previous == null ? new MusicWorkspaceState() : previous.state();
        if (queries.ready()) {
            UUID nearby = nearbyTarget(player);
            if (nearby != null) state.target(nearby);
        }
        if (previous != null && (previous.handle().state() == FloatingMenuState.ACTIVE
                || previous.handle().state() == FloatingMenuState.OPENING)) { previous.redraw(); return; }
        if (previous != null && previous.handle().state() == FloatingMenuState.SUSPENDED
                && FloatingMenus.current(player).filter(handle -> handle.id().equals(previous.handle().id())).isPresent()) {
            previous.handle().restore(); previous.redraw(); return;
        }
        if (state.panel() instanceof GroupEditor editor) editor.draft().pickingInWorld = false;
        closeCompanion(player);
        closeSearch(player.getUniqueId());
        if (previous != null) previous.close();
        flows.put(player.getUniqueId(), FloatingMenuFlow.openRoot(player, state, context -> render(context.player(), context.state())));
    }

    private UUID nearbyTarget(Player player) {
        record Candidate(UUID target, boolean playing, double distance) { }
        var candidates = new ArrayList<Candidate>();
        var at = player.getLocation();
        for (var target : registry.targets(player)) {
            double distance = target.members().stream().filter(device -> registry.inListeningRange(player,device) && registry.online(device))
                    .mapToDouble(device -> at.distanceSquared(registry.location(device).add(.5,.5,.5))).min().orElse(Double.POSITIVE_INFINITY);
            if (!Double.isFinite(distance) || !registry.mayControl(player,target.id())) continue;
            try {
                var snapshot = devices.snapshot(target.id());
                candidates.add(new Candidate(target.id(),snapshot.status() == PlaybackStatus.PLAYING,distance));
            } catch (MusicProblem unavailable) { /* Ignore obsolete playback targets while opening the library. */ }
        }
        return candidates.stream().min(Comparator.comparing((Candidate candidate) -> !candidate.playing())
                .thenComparingDouble(Candidate::distance).thenComparing(Candidate::target)).map(Candidate::target).orElse(null);
    }

    public void at(Player player, Jukebox box) {
        UUID opening = UUID.randomUUID();
        deviceOpenings.put(player.getUniqueId(),opening);
        var pick = worldPicks.get(player.getUniqueId());
        var selection = pick == null ? null : pick.draft();
        registry.discover(box).whenComplete((device, error) -> main(() -> {
            if (!player.isOnline() || !opening.equals(deviceOpenings.get(player.getUniqueId()))) return;
            if (error != null) {
                deviceOpenings.remove(player.getUniqueId(),opening);
                if (pick != null && worldPicks.remove(player.getUniqueId(),pick)) {
                    selection.pickingInWorld = false;
                    pick.lease().close();
                }
                error(player, error); return;
            }
            if (selection != null) {
                if (!pick.lease().active() || !owns(player,pick.state()) || !(pick.state().panel() instanceof GroupEditor editor)
                        || editor.draft() != selection || !selection.pickingInWorld) return;
                deviceOpenings.remove(player.getUniqueId(),opening);
                worldPicks.remove(player.getUniqueId(),pick); pick.lease().complete();
                selection.pickingInWorld = false;
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
                        if (!owns(player,pick.state()) || !(pick.state().panel() instanceof GroupEditor current) || current.draft() != selection) return;
                        if (failed != null) error(player,failed);
                        else safe(player,() -> selection.pick(device.id(),existing));
                        redraw(player);
                    }));
                });
                return;
            }
            safe(player, () -> {
                var current = registry.target(device.id()).members().getFirst();
                UUID target = current.group() == null ? current.id() : current.group();
                Runnable show = () -> safe(player, () -> {
                    if (!player.isOnline() || !opening.equals(deviceOpenings.get(player.getUniqueId()))) return;
                    // Membership may change while discovery or the personal record is being saved.
                    var latest = registry.target(device.id()).members().getFirst();
                    UUID selected = latest.group() == null ? latest.id() : latest.group();
                    registry.playbackTarget(selected);
                    open(player);
                    selectTarget(player,selected);
                    showDevices(player,new DevicePanel(selected,DeviceSection.PLAYBACK));
                    redraw(player);
                });
                if (registry.bound(player.getUniqueId(),target) || !registry.mayBind(player,target)) show.run();
                else registry.bind(player,target,"").whenComplete((_,failed) -> main(() -> {
                    if (!opening.equals(deviceOpenings.get(player.getUniqueId()))) return;
                    if (failed != null) { deviceOpenings.remove(player.getUniqueId(),opening); error(player,failed); }
                    else show.run();
                }));
            });
        }));
    }

    public MusicWorkspaceState state(Player player) { return flows.get(player.getUniqueId()).state(); }
    boolean owns(Player player, MusicWorkspaceState state) {
        var flow = flows.get(player.getUniqueId());
        return !closed && player.isOnline() && flow != null && (flow.state() == state || taskViews.containsKey(state) && taskViews.get(state).player().equals(player.getUniqueId()));
    }
    boolean active(Player player, MusicWorkspaceState state) {
        return owns(player, state) && (taskViews.containsKey(state) ? taskViews.get(state).handle().state() : flows.get(player.getUniqueId()).handle().state()) == FloatingMenuState.ACTIVE;
    }
    void pickMember(Player player, MusicWorkspaceState state, GroupDraft draft) {
        var view = taskViews.get(state);
        if (view == null || !active(player,state)) return;
        var previous = worldPicks.remove(player.getUniqueId());
        if (previous != null) previous.lease().close();
        draft.pickingInWorld = true;
        worldPicks.put(player.getUniqueId(),new WorldPick(state,draft,view.handle().suspend()));
        player.sendActionBar(label(player,"group-world-pick-hint"));
    }
    void selectTarget(Player player, UUID id) {
        deviceOpenings.remove(player.getUniqueId());
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
    UiNode panelHeading(Player player, MusicWorkspaceState state, Component title, UiNode... actions) {
        UiNode close = taskViews.containsKey(state) ? null : Ui.button(UiIcons.CROSS,label(player,"close-panel"))
                .key("close-panel").primary((_,_) -> panel(player,state,new NoPanel())).build();
        return MusicWorkspaceComposition.panelHeading(title, close, actions);
    }

    MenuScene render(Player player, MusicWorkspaceState state) {
        var builder = MenuScene.screen("music-workspace", label(player, "title")).stableAnchor().withoutTitle().viewport(MusicWorkspaceComposition.VIEWPORT)
                .refreshWhenChanged(20, viewer -> revision(viewer, state), (viewer, _) -> redraw(viewer))
                .lifecycle((viewer, _, previous, current, _) -> {
                    if (current == FloatingMenuState.SUSPENDED || current == FloatingMenuState.CLOSING
                            || current == FloatingMenuState.CLOSED) {
                        state.cancelMoves();
                        travel.cancel(viewer.getUniqueId());
                        closeSearch(viewer.getUniqueId());
                        this.transport.release(viewer.getUniqueId());
                    } else if (current == FloatingMenuState.OPENING && previous == FloatingMenuState.SUSPENDED) redraw(viewer);
                });
        UiNode browser = !queries.ready() ? info("unavailable", label(player, "unavailable"))
                : state.page == WorkspacePage.DEVICES ? outputs.directory(player, state, null) : library.browser(player, state);
        UiNode auxiliary = null;
        if (queries.ready()) {
            try {
                auxiliary = auxiliary(player, state);
            } catch (MusicProblem problem) {
                auxiliary = column("problem").child(panelHeading(player, state, problem(player, problem.code()))).build();
            }
        }
        if (auxiliary != null) auxiliary = MusicWorkspaceComposition.task(state.panelScope, auxiliary);
        UiNode actions = tools("workspace-actions")
                .child(Ui.button(UiIcons.CROSS,label(player,"close")).key("close").primary((_,handle) -> handle.close()).build()).build();
        locator.decorate(player,state,builder);
        UiNode content = MusicWorkspaceComposition.content(state.focusedTask() ? null : library.navigation(player, state), browser, auxiliary,
                queries.ready() ? this.transport.content(player, state) : info("waiting", label(player, "unavailable")), actions);
        if (auxiliary != null && !state.focusedTask()) {
            content = Ui.alternative(content,MusicWorkspaceComposition.content(null,browser,auxiliary,
                    this.transport.compact(player,state),actions));
        }
        if (searchInputs.containsKey(player.getUniqueId())) {
            boolean[] visible = {false};
            UiTree.visit(content, (_, node) -> { if (node.key().equals(SEARCH_EDITOR)) visible[0] = true; });
            if (!visible[0]) closeSearch(player.getUniqueId());
        }
        return builder.content(content).build();
    }

    private Object revision(Player player, MusicWorkspaceState state) {
        var statuses = new ArrayList<Object>();
        statuses.add(language.language(player));
        statuses.add(queries.revision());
        statuses.add(locator.visible(player,state));
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

    private UiNode auxiliary(Player player, MusicWorkspaceState state) {
        Panel panel = state.panel();
        if (panel instanceof NoPanel) return null;
        return switch (panel) {
            case Outputs picker -> outputs.directory(player, state, picker.pending());
            case DevicePanel device -> outputs.details(player, state, device);
            case MemberOutput member -> outputs.memberOutput(player, state, member);
            case GroupEditor editor -> outputs.groupEditor(player, state, editor.draft());
            case Import task -> imports.content(player, state, task);
            case RoomPicker _ -> games.picker(player, state);
            case Room room -> games.room(player, state, room);
            case NoPanel _ -> throw new IllegalStateException();
        };
    }

    void panel(Player player, Panel next) { panel(player,state(player),next); }
    MusicWorkspaceState panel(Player player, MusicWorkspaceState owner, Panel next) {
        if (!owns(player,owner)) return owner;
        var current = taskViews.get(owner);
        if (current != null && (next == current.owner().panel() || owner.panel() instanceof GroupEditor && next instanceof DevicePanel device
                && current.owner().panel() instanceof DevicePanel parent && parent.target().equals(device.target()))) {
            current.owner().panel(next); current.handle().close(); redraw(player); return current.owner();
        }
        if (current != null && (next instanceof NoPanel || next instanceof DevicePanel device && Objects.equals(device.target(),owner.target())
                || next instanceof MemberOutput)) {
            if (next instanceof NoPanel) current.handle().close();
            else { owner.panel(next); redraw(player); }
            return owner;
        }
        if (next instanceof NoPanel) { owner.panel(next); redraw(player); return owner; }
        if (current != null && next instanceof DevicePanel) {
            current.handle().close();
            return panel(player,state(player),next);
        }
        String identity = switch (next) {
            case DevicePanel device -> "device:" + device.target();
            case MemberOutput member -> "member:" + member.group() + ":" + member.member();
            case GroupEditor editor -> "group-editor:" + editor.draft().task;
            case Room room -> "room:" + room.device();
            case Outputs _ -> "outputs";
            case Import _ -> "import";
            case RoomPicker _ -> "rooms";
            case NoPanel _ -> throw new IllegalStateException();
        };
        var root = flows.get(player.getUniqueId()).handle();
        var parent = current == null ? root : current.handle();
        var local = new MusicWorkspaceState(state(player).tasks);
        local.target(next instanceof DevicePanel device ? device.target() : owner.target());
        local.panel(next);
        var spec = next instanceof DevicePanel ? MenuWindowSpec.tool(UiKey.item(identity)) : MenuWindowSpec.task(UiKey.item(identity));
        var handle = parent.openWindow(spec, context -> {
            taskViews.put(local,new TaskView(player.getUniqueId(),context.handle(),owner));
            return new MenuPanel() {
                private boolean disposed;
                public MenuScene render() {
                    UiNode body;
                    try { body = auxiliary(player,local); }
                    catch (MusicProblem unavailable) { body = info("unavailable",problem(player,unavailable.code())); }
                    body = MusicWorkspaceComposition.task(local.panelScope,body);
                    if (local.panel() instanceof DevicePanel device && device.section() != DeviceSection.PLAYBACK)
                        body = UiTemplates.sheet(body,transport.compact(player,local));
                    return MenuScene.screen("music-task",local.panel() instanceof DevicePanel device ? targetSummary(player,device.target()) : label(player,"title"))
                            .viewport(MusicWorkspaceComposition.VIEWPORT).content(body)
                            .refreshWhenChanged(20,viewer -> revision(viewer,local),(viewer,_) -> redraw(viewer)).build();
                }
                public void close() {
                    if (disposed) return;
                    disposed = true; taskViews.remove(local); local.cancelMoves(); transport.release(local);
                    var pick = worldPicks.get(player.getUniqueId());
                    if (pick != null && pick.state() == local) { worldPicks.remove(player.getUniqueId(),pick); pick.lease().close(); }
                    if (local.panel() instanceof GroupEditor editor) editor.draft().pickingInWorld = false;
                }
            };
        });
        return taskViews.entrySet().stream().filter(entry -> entry.getValue().handle().id().equals(handle.id()))
                .map(Map.Entry::getKey).findFirst().orElse(local);
    }
    void returnToMembers(Player player, MusicWorkspaceState state, MemberOutput source) {
        if (!active(player,state) || state.panel() != source) return;
        closeCompanion(player);
        if (state.returnToMembers(source)) redraw(player);
    }
    void toggleDeviceIdentity(Player player, MusicWorkspaceState state, Panel owner, DeviceIdentityView view) {
        if (!active(player,state) || !state.toggleDeviceIdentity(owner,view)) return;
        redraw(player);
    }
    void showRooms(Player player) {
        closeCompanion(player);
        closeSearch(player.getUniqueId());
        var room = rooms.current(player.getUniqueId());
        panel(player,state(player),room == null ? new RoomPicker() : new Room(room.device()));
    }
    void showDevices(Player player) {
        var state = state(player);
        closeCompanion(player);
        closeSearch(player.getUniqueId());
        state.devices();
        redraw(player);
    }
    void showDevices(Player player, Panel next) {
        closeCompanion(player);
        closeSearch(player.getUniqueId());
        var state = state(player);
        panel(player,state,next);
    }
    void browse(Player player, Collection collection) {
        closeCompanion(player);
        closeSearch(player.getUniqueId());
        var state = state(player);
        state.searchGeneration++;
        state.tasks.remove(TaskKey.of(Work.SEARCH));
        state.browse(collection);
        redraw(player);
    }
    void browsePlaylist(Player player, UUID id, Source source) {
        closeCompanion(player);
        closeSearch(player.getUniqueId());
        var state = state(player);
        state.searchGeneration++;
        state.tasks.remove(TaskKey.of(Work.SEARCH));
        state.browsePlaylist(id, source);
        redraw(player);
    }
    void window(Player player, UiKey source, Function<MenuWindowHandle<Void>, MenuPanel> root) {
        var flow = flows.get(player.getUniqueId());
        if (flow == null || !active(player, flow.state())) return;
        flow.state().windowGeneration++;
        FloatingMenus.current(player).orElse(flow.handle()).openWindow(MenuWindowSpec.task(source), context -> root.apply(context.handle()));
    }
    void input(Player player, UiKey source, MenuInputForm form, String focus, boolean editOnAttach) {
        MenuFormPanel.open(FloatingMenus.current(player).orElse(flows.get(player.getUniqueId()).handle()), source, player, form, focus, editOnAttach);
    }
    void input(Player player, FloatingMenuHandle owner, MenuInputForm form, String focus) {
        MenuFormPanel.open(owner, UiKey.item("form:" + focus), player, form, focus, false);
    }
    UiNode field(Player player, String id, String key, String value, int maximum, Consumer<String> apply) {
        return form(player, id, inputForm(player, key, value, maximum, apply));
    }
    UiNode asyncField(Player player, String id, String key, String value, int maximum, MenuInputForm.AsyncSubmit save) {
        return form(player,id,MenuInputForm.builder(label(player,key))
                .field(MenuInputForm.Field.text("value",label(player,key),value,maximum))
                .validate((viewer,values) -> {
                    try { Playlist.text(values.text("value"),maximum); return null; }
                    catch (IllegalArgumentException invalid) { return problem(viewer,MusicProblem.Code.INVALID); }
                }).cancel(label(player,"cancel")).submitAsync(label(player,"apply"),save).build());
    }
    UiNode form(Player player, String id, MenuInputForm form) {
        var fields = Ui.form().key(id);
        for (var field : form.fields()) {
            var source = UiKey.item(id + ":" + field.key());
            fields.field(field.key(), field.label(), Ui.textField(field.initial()).preview(field.preview())
                    .key(field.key()).previewKey(source).placeholder(language.text(player, Message.MENU_INPUT_EMPTY, NamedTextColor.AQUA))
                    .maxLines(field.multiline() ? 4 : 1)
                    .primary((viewer, _) -> safe(viewer, () -> input(viewer, source, form, field.key(), true))).build());
        }
        return fields.build();
    }
    void input(Player player, UiKey source, String key, String initial, int maximum, Consumer<String> apply) {
        input(player, source, inputForm(player, key, initial, maximum, apply), "value", false);
    }
    private MenuInputForm inputForm(Player player, String key, String initial, int maximum, Consumer<String> apply) {
        return MenuInputForm.builder(label(player, key))
                .field(MenuInputForm.Field.text("value", label(player, key), initial, maximum).multiline(key.equals("description"))
                        .inputMethod(key.equals("description") ? MenuInputForm.InputMethod.DIALOG : MenuInputForm.InputMethod.CHAT))
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
    private void closeCompanion(Player player) {
        var flow = flows.get(player.getUniqueId());
        if (flow != null) {
            flow.state().windowGeneration++;
            if (flow.handle().state() == FloatingMenuState.ACTIVE) FloatingMenus.current(player).ifPresent(FloatingMenuHandle::closePopover);
        }
    }

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
    <T> void persist(Player player, TaskKey key, Supplier<CompletableFuture<T>> operation, Consumer<T> applied, MenuInputForm.Completion completion) {
        var state = state(player);
        if (state.busy(key)) { completion.failure(problem(player,MusicProblem.Code.BUSY)); return; }
        long ticket = state.begin(key);
        CompletableFuture<T> future;
        try { future = operation.get(); }
        catch (Exception error) {
            var code = failure(player,error); state.finish(key,ticket,code); completion.failure(problem(player,code)); redraw(player); return;
        }
        redraw(player);
        future.whenComplete((result,error) -> main(() -> {
            var code = error == null ? null : failure(player,error);
            state.finish(key,ticket,code);
            if (error != null) completion.failure(problem(player,code));
            else {
                try { if (owns(player,state)) applied.accept(result); completion.success(); }
                catch (Exception failed) { completion.failure(problem(player,failure(player,failed))); }
            }
            if (owns(player,state)) redraw(player);
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
        if (state.target() != null) {
            try { registry.playbackTarget(state.target()); }
            catch (MusicProblem obsolete) {
                if (obsolete.code() != MusicProblem.Code.NOT_FOUND && obsolete.code() != MusicProblem.Code.CONFLICT) throw obsolete;
                state.target(null);
            }
        }
        if (state.target() == null) { panel(player, new Outputs(new PendingPlay(tracks, placement))); return; }
        submit(player, new MusicSubmission(player.getUniqueId(), state.target(), tracks, placement));
    }
    void submit(Player player, MusicSubmission request) {
        var state = state(player);
        if (state.busy(new TaskKey(Work.PLAYBACK, request.target()))) return;
        state.lastSubmission = request;
        run(player, new TaskKey(Work.PLAYBACK, request.target()), () -> devices.submit(player, request), _ -> {});
    }
    void confirm(Player player, UiKey source, Decision decision) {
        String key = switch (decision) {
            case DeletePlaylist _ -> "delete";
            case ClearRecents _ -> "clear-recents";
            case EndRoom _ -> "end-room";
        };
        var owner = FloatingMenus.current(player).orElseThrow();
        var rootState = state(player);
        long revision = rootState.browser.revision;
        owner.openWindow(MenuWindowSpec.task(UiKey.item("confirm:" + source.value())), context -> new MenuPanel() {
            private boolean busy, disposed;
            private Component failure;
            public MenuScene render() {
                var body = column("confirmation");
                if (decision instanceof DeletePlaylist list) body.child(info("scope",language.text(player,Message.MUSIC_SPACE_DELETE_CONFIRMATION,NamedTextColor.YELLOW,list.name())));
                if (decision instanceof ClearRecents) body.child(info("scope",language.text(player,Message.MUSIC_SPACE_CLEAR_RECENTS_CONFIRMATION,NamedTextColor.YELLOW)));
                if (decision instanceof EndRoom) body.child(info("scope",label(player,"room-end-confirmation")));
                if (failure != null) body.child(info("failure",failure));
                var apply = Ui.button(label(player,key)).key("apply").primary((_,_) -> apply());
                if (busy) apply.disabled(label(player,"saving"));
                body.child(apply.build()).child(Ui.button(label(player,"cancel")).key("cancel").primary((_,_) -> context.handle().close()).build());
                return MenuScene.screen("music-confirm",label(player,key)).content(body.build()).build();
            }
            private void apply() {
                if (busy || disposed) return;
                busy = true; failure = null; context.redraw().run();
                var completion = new MenuInputForm.Completion() {
                    public void success() {
                        if (disposed) return;
                        context.handle().close();
                        if (decision instanceof DeletePlaylist) {
                            if (owner instanceof MenuWindowHandle<?>) owner.close();
                            if (owns(player,rootState) && revision == rootState.browser.revision
                                    && decision instanceof DeletePlaylist list && list.id().equals(rootState.browser.collection.playlist()))
                                browse(player,rootState.browser.parent);
                        } else if (decision instanceof EndRoom && owner instanceof MenuWindowHandle<?>) owner.close();
                    }
                    public void failure(Component problem) { if (!disposed) { busy = false; failure = problem; context.redraw().run(); } }
                };
                switch (decision) {
                    case DeletePlaylist list -> persist(player,new TaskKey(Work.LIBRARY,list.id()),
                            () -> playlists.delete(player.getUniqueId(),list.id(),list.version()),_ -> {},completion);
                    case ClearRecents _ -> persist(player,new TaskKey(Work.LIBRARY,player.getUniqueId()),
                            () -> playlists.clearRecents(player.getUniqueId()),_ -> {},completion);
                    case EndRoom room -> {
                        try { rooms.end(player,room.device()); completion.success(); }
                        catch (Exception error) { completion.failure(problem(player,MusicWorkspace.this.failure(player,error))); }
                    }
                }
            }
            public void close() { disposed = true; }
        });
    }

    public void search(Player player, String query) {
        open(player);
        var state = state(player);
        String clean = cleanQuery(query);
        if (!clean.isBlank()) acquire(player);
        closeCompanion(player);
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
        deviceOpenings.remove(player);
        var pick = worldPicks.remove(player); if (pick != null) pick.lease().close();
        this.transport.release(player);
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
        deviceOpenings.clear();
        sound.close();
        transport.close();
        List.copyOf(searchInputs.keySet()).forEach(this::closeSearch);
        List.copyOf(flows.values()).forEach(FloatingMenuFlow::close);
        flows.clear();
    }
}
