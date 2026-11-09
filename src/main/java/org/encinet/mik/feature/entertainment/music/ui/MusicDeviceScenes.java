package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.application.MusicGroupService.MAX_MEMBERS;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.column;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.info;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.tools;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.*;
import org.encinet.mik.feature.entertainment.music.jukebox.*;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.feature.entertainment.music.ui.MusicSoundEdits.Parameter;

/** One device or sync group, with a shared directory and one playback inspector. */
final class MusicDeviceScenes {
    private final MusicWorkspace w;
    private final MusicGroupScenes groups;
    private final MusicQueueScenes queue;
    MusicDeviceScenes(MusicWorkspace workspace) {
        w = workspace; groups = new MusicGroupScenes(workspace); queue = new MusicQueueScenes(workspace);
    }
    String name(Player player, UUID id) {
        try { return w.registry.name(player, id); }
        catch (MusicProblem missing) { return id.toString().substring(0, 8); }
    }
    private TaskKey key(UUID id) { return new TaskKey(Work.DEVICE, id); }

    MenuPanel picker(Player player, MusicWorkspaceState state, Runnable redraw) {
        return new MenuPanel() {
            private int page;
            private boolean active = true;
            public MenuScene render() {
                if (!active) return MenuScene.screen("music-target-picker-closed").content(Ui.column().build()).build();
                return MenuScene.screen("music-target-picker", w.label(player, "devices")).content(directory(player, state, null, null,
                        page,next -> { if (active) page = next; },() -> { if (active) redraw.run(); },true,(id,handle) -> {
                            if (!active) return;
                            handle.closePopover();
                            w.selectTarget(player, id);
                            w.redraw(player);
                        })).build();
            }
            @Override public void close() { active = false; }
        };
    }
    UiNode directory(Player player, MusicWorkspaceState state, PendingPlay pending) {
        GroupDraft draft = pending == null && state.page == WorkspacePage.DEVICES && state.panel() instanceof GroupEditor editor ? editor.draft() : null;
        var pageScope = state.page; var panelScope = state.panel();
        return directory(player,state,pending,draft,state.devicePage,
                page -> { if (state.page == pageScope && state.panel() == panelScope) state.devicePage = page; },
                () -> { if (state.page == pageScope && state.panel() == panelScope) w.redraw(player); },false,(id,handle) -> {
            if (state.page != pageScope || state.panel() != panelScope) return;
            if (draft != null) {
                if (state.busy(new TaskKey(Work.DEVICE,draft.group))) throw new MusicProblem(MusicProblem.Code.BUSY);
                draft.toggle(id); w.redraw(player); return;
            }
            handle.closePopover();
            w.selectTarget(player, id);
            if (pending != null) { w.panel(player, new NoPanel()); w.submit(player, pending.tracks(), pending.placement()); }
            else if (state.panel() instanceof DevicePanel) w.redraw(player);
            else w.panel(player, new DevicePanel(id, DeviceSection.QUEUE));
        });
    }
    private UiNode directory(Player player, MusicWorkspaceState state, PendingPlay pending, GroupDraft draft,
                             int page, IntConsumer changePage, Runnable redraw, boolean companion, BiConsumer<UUID, FloatingMenuHandle> choose) {
        var targets = new ArrayList<>(w.registry.targets(player));
        if (draft != null) targets.removeIf(target -> w.registry.snapshot().groups().containsKey(target.id()));
        targets.sort(Comparator.comparing(DeviceTarget::name).thenComparing(DeviceTarget::id));
        var actions = new ArrayList<UiNode>();
        if (draft == null) actions.add(Ui.button(w.label(player, "create-group")).key("create-group").primary((_, handle) -> {
            handle.closePopover(); w.safe(player, () -> groups.create(player, state, pending));
        }).build());
        var title = w.label(player, draft == null ? "devices" : draft.group == null ? "group-select-members" : "choose-members");
        var heading = pending != null ? w.panelHeading(player, title, actions.toArray(UiNode[]::new))
                : UiTemplates.titleBar(Ui.heading(title).key("title").build(), actions.isEmpty() ? null : tools("device-directory-actions").children(actions).build());
        var body = column("device-directory");
        if (!companion) body.child(heading);
        else if (!actions.isEmpty()) body.child(tools("device-directory-actions").children(actions).build());
        if (pending != null) body.child(info("pending", w.label(player, MusicPlaybackActions.labelKey(pending.placement()))
                .append(w.raw(" · ")).append(w.raw(pending.tracks().getFirst().details().title()))).withSummaryLines(1));
        var rows = new ArrayList<MusicDeviceTable.Row>();
        for (var target : targets) {
            boolean group = w.registry.snapshot().groups().containsKey(target.id());
            long online = target.members().stream().filter(w.registry::online).count();
            var status = !group ? w.label(player,"available") : online == target.members().size() ? w.label(player,"all-online")
                    : w.language.text(player, Message.MUSIC_SPACE_ONLINE_COUNT, NamedTextColor.AQUA, online, target.members().size());
            MusicProblem.Code restriction = w.registry.controlFailure(player, target.id());
            if (draft != null && draft.group != null && !draft.members.contains(target.id())) {
                var editingGroup = w.registry.snapshot().groups().get(draft.group);
                if (editingGroup == null) restriction = MusicProblem.Code.NOT_FOUND;
                else if (draft.members.size() >= MAX_MEMBERS - editingGroup.members().size()) restriction = MusicProblem.Code.LIMIT;
            }
            Component song = Component.text("—", NamedTextColor.WHITE);
            if (online > 0) {
                try {
                    var current = w.devices.current(target.id());
                    if (current != null) song = w.raw(current.details().title());
                } catch (MusicProblem unavailable) { restriction = unavailable.code(); }
            }
            var kind = w.label(player,group ? "sync-group" : "single-device").append(w.raw(" · "))
                    .append(w.label(player,w.registry.bound(player.getUniqueId(),target.id()) ? "recorded-device" : "nearby-device"));
            rows.add(new MusicDeviceTable.Row(target.id(), w.raw(name(player, target.id())), kind,
                    restriction == null ? status : w.problem(player, restriction), song,
                    (pending != null || draft != null) && restriction != null ? w.problem(player, restriction) : null,
                    w.locator.position(player,target.id()),w.locator.coordinates(player,target.id()),w.locator.color(target.id())));
        }
        if (rows.isEmpty()) body.child(info("empty-devices", w.label(player, "no-devices")))
                .child(info("device-discovery-hint", w.label(player, "device-discovery-hint")));
        else body.child(MusicDeviceTable.content(rows, state.target(), draft == null ? null : draft.members, key -> w.label(player, key),
                id -> (_,handle) -> w.safe(player,() -> choose.accept(id,handle)),page,next -> { changePage.accept(next); redraw.run(); }));
        return UiTemplates.reading(body.build(),UiKey.named(rows.isEmpty() ? "empty-devices" : "device-list"),UiLength.ui(8));
    }
    UiNode details(Player player, MusicWorkspaceState state, DevicePanel panel) {
        var target = w.registry.playbackTarget(panel.target());
        UUID id = target.id();
        var group = w.registry.snapshot().groups().get(target.id());
        var actions = new ArrayList<UiNode>();
        if (!w.registry.bound(player.getUniqueId(), id) && w.registry.mayBind(player, id)) {
            var record = Ui.button(w.label(player, "record")).key("record")
                    .primary((_, _) -> w.safe(player, () -> w.run(player, key(id), () -> w.registry.bind(player, id, ""), _ -> {})));
            if (state.busy(key(id))) record.disabled(w.label(player, "saving"));
            actions.add(record.build());
        }
        var body = Ui.layout(UiLayouts.contentColumn(UiKey.named("device-section"),UiLength.ui(8))).key("device-inspector").child(w.panelHeading(player, w.targetSummary(player, id), actions.toArray(UiNode[]::new)));
        body.child(w.deviceLocation(player,id));
        var tabs = tools("device-tabs");
        for (var section : DeviceSection.values()) {
            if (section == DeviceSection.MEMBERS && group == null) continue;
            tabs.child(w.choice(player, "section:" + section, w.label(player, "device-" + section.name().toLowerCase(Locale.ROOT)),
                    panel.section() == section, () -> w.panel(player, new DevicePanel(panel.target(), section))));
        }
        body.child(tabs.build());
        return body.child(UiKey.named("device-section"),switch (panel.section()) {
            case QUEUE -> queue.content(player, state, panel.target());
            case MEMBERS -> group == null ? info("no-group", w.label(player, "single-device")) : groups.members(player, state, group);
            case SETTINGS -> settings(player, state, target);
        }).children(w.taskStatus(player, state, key(id))).build();
    }
    private UiNode settings(Player player, MusicWorkspaceState state, DeviceTarget target) {
        UUID id = target.id();
        var group = w.registry.snapshot().groups().get(id);
        var body = column("device-settings");
        var failure = w.registry.controlFailure(player, id);
        boolean editable = failure == null;
        if (failure != null) body.child(info("unavailable", w.problem(player, failure)));
        if (group == null && editable) body.child(outputSettings(player, target.members().getFirst()));
        else if (group != null && editable) body.child(w.sound.fields(player, id, Parameter.GAIN));
        boolean bound = w.registry.bound(player.getUniqueId(), id);
        if (editable || bound) {
            var owner = state.panel(); var view = state.deviceIdentity;
            Component title = editable ? w.label(player,"name") : w.label(player,"alias");
            if (editable && bound) title = title.append(w.raw(" · ")).append(w.label(player,"alias"));
            Component action = w.language.text(player,view.expanded() ? Message.MENU_COLLAPSE : Message.MENU_EXPAND,NamedTextColor.AQUA);
            var toggle = Ui.button(view.expanded() ? UiIcons.CHEVRON_UP : UiIcons.CHEVRON_DOWN,action)
                    .key("toggle").selected(view.expanded()).tooltip(title.append(Component.newline()).append(action))
                    .primary((_,_) -> w.toggleDeviceIdentity(player,state,owner,view));
            if (w.editing(player)) toggle.disabled(w.language.text(player,Message.MENU_FINISH_EDITING,NamedTextColor.AQUA));
            body.child(UiTemplates.disclosure(UiKey.named("device-identity"),Ui.text(title).key("summary").summaryLines(1).tooltip(title).build(),
                    toggle.build(),view.expanded(),() -> {
                        var fields = new java.util.ArrayList<UiNode>();
                        if (editable) fields.add(w.field(player,"device-name","name",target.name(),64,value -> {
                            if (w.active(player,state) && state.panel() == owner && state.deviceIdentity == view)
                                w.run(player,key(id),() -> w.groups.rename(player,id,value),_ -> {});
                        }));
                        if (bound) fields.add(w.field(player,"device-alias","alias",w.registry.name(player,id),64,value -> {
                            if (w.active(player,state) && state.panel() == owner && state.deviceIdentity == view)
                                w.run(player,key(id),() -> w.registry.alias(player.getUniqueId(),id,value),_ -> {});
                        }));
                        return identityFields(fields);
                    }));
        }
        var actions = tools("device-actions");
        if (bound) actions.child(w.button(player, "forget", () -> w.run(player, key(id), () -> w.registry.unbind(player.getUniqueId(), id), _ -> {})));
        if (group == null && editable) {
            actions.child(w.button(player, "eject", () -> w.devices.eject(player, id)));
        } else if (group != null && editable) actions.child(groups.removal(player, state, group, null));
        return body.child(actions.build()).build();
    }
    static UiNode identityFields(java.util.List<UiNode> fields) {
        return Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(12)),UiLayouts.column(UiLength.ui(8))))
                .key("identity-fields").children(fields).build();
    }

    UiNode memberOutput(Player player, MusicWorkspaceState state, MemberOutput panel) {
        var group = w.registry.target(panel.group());
        var member = group.members().stream().filter(device -> device.id().equals(panel.member())).findFirst()
                .orElseThrow(() -> new MusicProblem(MusicProblem.Code.NOT_FOUND));
        var failure = w.registry.controlFailure(player, member.id());
        var body = column("member-output").child(w.panelHeading(player, w.raw(name(player, member.id())),
                        w.button(player, "back", () -> w.returnToMembers(player,state,panel))))
                .child(w.deviceLocation(player,member.id()))
                .child(failure == null ? outputSettings(player, member) : info("unavailable", w.problem(player, failure)));
        return body.children(w.taskStatus(player, state, key(member.id()))).build();
    }
    private UiNode outputSettings(Player player, MusicDevice device) {
        return w.sound.fields(player, device.id(), Parameter.VOLUME, Parameter.RANGE);
    }
    UiNode groupEditor(Player player, MusicWorkspaceState state, GroupDraft draft) { return groups.editor(player, state, draft); }
}
