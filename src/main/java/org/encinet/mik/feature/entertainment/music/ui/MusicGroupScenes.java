package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.application.MusicGroupService.MAX_MEMBERS;
import static org.encinet.mik.feature.entertainment.music.application.MusicGroupService.MIN_MEMBERS;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.*;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** Membership drafts are separate from the active playback target and are only applied on submission. */
final class MusicGroupScenes {
    private final MusicWorkspace w;
    MusicGroupScenes(MusicWorkspace workspace) { w = workspace; }

    void create(Player player, MusicWorkspaceState state, PendingPlay pending, UUID seed) {
        var draft = new GroupDraft(null, pending);
        draft.name = w.text(player,"sync-group");
        draft.returnTo = state.panel();
        if (seed != null && !w.registry.snapshot().groups().containsKey(seed) && w.registry.mayControl(player, seed)) draft.toggle(seed);
        w.showDevices(player,new GroupEditor(draft));
    }
    private record SourceChoice(UUID device, boolean removed) { }

    UiNode editor(Player player, MusicWorkspaceState state, GroupDraft draft) {
        var scope = state.panelScope;
        java.util.function.BooleanSupplier owned = () -> w.active(player,state) && scope.equals(state.panelScope)
                && state.panel() instanceof GroupEditor editor && editor.draft() == draft;
        boolean creating = draft.group == null;
        var group = creating ? null : w.registry.snapshot().groups().get(draft.group);
        if (!creating && group == null) return column("missing-group")
                .child(w.panelHeading(player, w.label(player, "add-members")))
                .child(info("missing", w.problem(player, MusicProblem.Code.NOT_FOUND))).build();
        long version = group == null ? 0 : group.version();
        boolean busy = state.busy(new TaskKey(Work.DEVICE,draft.group));
        var body = Ui.layout(UiLayouts.contentColumn(UiKey.named("member-selection"),UiLength.ui(8))).key("group-editor")
                .child(MusicWorkspaceComposition.panelHeading(w.label(player, creating ? "create-group" : "add-members"),
                        w.button(player,"cancel",() -> { if (owned.getAsBoolean()) w.panel(player,draft.returnTo); })));
        if (group != null) body.child(info("group", w.targetSummary(player, group.id())).withSummaryLines(1));
        var selectionTools = tools("member-selection-tools")
                .child(info("count", w.language.text(player, Message.MUSIC_SPACE_DEVICE_COUNT, NamedTextColor.AQUA, draft.members.size())));
        if (creating) {
            var name = Ui.button(w.label(player,"name").append(w.raw(" · ")).append(w.raw(draft.name)))
                .key("group-name").summaryLines(1).textWidth(200).primary((_,_) -> {
                    if (!owned.getAsBoolean() || state.busy(new TaskKey(Work.DEVICE,draft.group))) return;
                    w.input(player,"name",draft.name,64,value -> {
                        if (!owned.getAsBoolean() || state.busy(new TaskKey(Work.DEVICE,draft.group))) return;
                        draft.name = value; w.redraw(player);
                    });
                });
            if (busy) name.disabled(w.label(player,"saving"));
            selectionTools.child(name.build());
        }
        var pick = Ui.button(w.label(player,"pick-world-member")).key("pick-world-member")
                .tooltip(w.label(player,"group-selection-help")).primary((_,handle) -> {
            if (!owned.getAsBoolean() || state.busy(new TaskKey(Work.DEVICE,draft.group))) return;
            draft.pickingInWorld = true;
            handle.close();
            player.sendActionBar(w.label(player,"group-world-pick-hint"));
        });
        if (busy) pick.disabled(w.label(player,"saving"));
        else if (draft.members.size() + (group == null ? 0 : group.members().size()) >= MAX_MEMBERS)
            pick.disabled(w.problem(player,MusicProblem.Code.LIMIT));
        body.child(selectionTools.child(pick.build()).build())
                .child(UiKey.named("member-selection"),w.outputs.directory(player,state,null));
        var missingMembers = draft.members.stream().filter(id -> {
            var device = w.registry.snapshot().devices().get(id);
            return device == null || device.group() != null;
        }).toList();
        boolean choosingSource = creating && draft.members.stream().anyMatch(this::hasContent);
        var rows = new ArrayList<MusicTaskEntries.Row<SourceChoice>>();
        for (UUID id : draft.members) {
            boolean removed = missingMembers.contains(id);
            if (removed) {
                Component label = w.label(player,"removed-targets");
                rows.add(new MusicTaskEntries.Row<>(UiKey.item("missing:" + id),new SourceChoice(id,true),label,label,false,
                        busy ? w.label(player,"saving") : null));
            } else if (choosingSource) {
                var target = w.registry.snapshot().devices().get(id);
                var program = w.devices.program(id);
                var current = program.current() == null ? null : w.queries.snapshot(player.getUniqueId()).tracks().get(program.current().trackId());
                int count = program.base().size() + program.priority().size() + (program.current() == null ? 0 : 1);
                Component content = current != null ? w.raw(current.title()) : count > 0 ? w.trackCount(player,count) : w.label(player,"empty");
                Component label = w.locator.name(player,target).append(w.raw(" · ")).append(content);
                Component help = label.append(Component.newline()).append(w.locator.position(player,id))
                        .append(Component.newline()).append(w.locator.coordinates(player,id));
                rows.add(new MusicTaskEntries.Row<>(UiKey.item("source:" + id),new SourceChoice(id,false),label,help,id.equals(draft.source),
                        busy ? w.label(player,"saving") : null));
            }
        }
        if (!rows.isEmpty()) {
            var source = UiKey.named("group-source");
            var sourceName = missingMembers.isEmpty() && draft.source != null ? w.raw(w.outputs.name(player,draft.source)) : w.label(player,"removed-targets");
            var choose = Ui.button(w.label(player,"queue").append(w.raw(" · ")).append(sourceName)).key(source)
                    .summaryLines(1).textWidth(240).tooltip(w.label(player,"group-source").append(w.raw(" · ")).append(sourceName)
                            .append(Component.newline()).append(w.label(player,"group-source-help")))
                    .primary((_,handle) -> handle.openPopover(source,redraw -> new MenuPanel() {
                        private MusicSourcePosition position = MusicSourcePosition.START;
                        private boolean active = true;
                        public MenuScene render() {
                            return MenuScene.screen("music-group-source",w.label(player,"group-source"))
                                    .content(MusicTaskEntries.content("group-sources",rows,w.label(player,"empty"),position,next -> {
                                        if (active && owned.getAsBoolean()) { position = next; redraw.run(); }
                                    },choice -> (_,popup) -> w.safe(player,() -> {
                                        if (!active || !owned.getAsBoolean() || state.busy(new TaskKey(Work.DEVICE,draft.group))) return;
                                        var current = w.registry.snapshot().devices().get(choice.device());
                                        boolean removed = current == null || current.group() != null;
                                        if (removed != choice.removed() || !draft.members.contains(choice.device())) { popup.closePopover(); w.redraw(player); return; }
                                        if (removed) draft.toggle(choice.device()); else draft.source = choice.device();
                                        popup.closePopover(); w.redraw(player);
                                    }))).build();
                        }
                        @Override public void close() { active = false; }
                    }));
            if (busy) choose.disabled(w.label(player,"saving"));
            body.child(choose.build());
        }
        if (draft.pending != null) body.child(info("pending", w.label(player, MusicPlaybackActions.labelKey(draft.pending.placement()))
                .append(w.raw(" · ")).append(w.raw(draft.pending.tracks().getFirst().details().title()))).withSummaryLines(1));
        var actions = tools("group-editor-actions");
        if (creating) {
            String missing = busy ? "saving" : draft.members.size() < MIN_MEMBERS ? "group-select-members"
                    : !missingMembers.isEmpty() ? "removed-targets" : draft.name.isBlank() ? "group-name-required"
                    : !draft.members.contains(draft.source) ? "group-source" : null;
            if (draft.pending == null && draft.source != null && hasContent(draft.source)) {
                var play = Ui.button(w.label(player,"create-play")).key("create-play")
                        .primary((_,_) -> w.safe(player,() -> { if (owned.getAsBoolean()) save(player,state,draft,version,true); }));
                if (missing != null) play.disabled(w.label(player,missing));
                actions.child(play.build());
            }
            String key = draft.pending == null ? "create-group" : draft.pending.placement() == org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram.Submission.INSERT
                    ? "create-play" : "create-and-append";
            var create = Ui.button(w.label(player,key)).key(key)
                    .primary((_,_) -> w.safe(player,() -> { if (owned.getAsBoolean()) save(player,state,draft,version,draft.pending != null); }));
            if (missing != null) create.disabled(w.label(player,missing));
            actions.child(create.build());
        } else {
            var add = Ui.button(w.label(player,"add-members")).key("add-members").primary((_,_) -> w.safe(player,() -> {
                if (owned.getAsBoolean()) save(player,state,draft,version,false);
            }));
            if (busy) add.disabled(w.label(player,"saving"));
            else if (draft.members.isEmpty()) add.disabled(w.label(player,"choose-members"));
            else if (group.members().size() + draft.members.size() > MAX_MEMBERS) add.disabled(w.problem(player,MusicProblem.Code.LIMIT));
            else if (!missingMembers.isEmpty()) add.disabled(w.label(player,"removed-targets"));
            actions.child(add.build());
        }
        return body.child(actions.build()).children(w.taskStatus(player, state, new TaskKey(Work.DEVICE, draft.group))).build();
    }
    private boolean hasContent(UUID id) {
        var device = w.registry.snapshot().devices().get(id);
        if (device == null || device.group() != null) return false;
        var program = w.devices.program(id);
        return program.current() != null || !program.base().isEmpty() || !program.priority().isEmpty();
    }
    private void save(Player player, MusicWorkspaceState state, GroupDraft draft, long version, boolean play) {
        Set<UUID> members = Set.copyOf(draft.members);
        if (draft.group != null) {
            w.run(player, new TaskKey(Work.DEVICE, draft.group), () -> w.groups.addMembers(player, draft.group, version, members), _ -> {
                if (state.panel() instanceof GroupEditor current && current.draft() == draft) {
                    w.panel(player, new DevicePanel(draft.group, DeviceSection.MEMBERS));
                }
            });
            return;
        }
        UUID source = draft.source;
        String name = draft.name;
        w.run(player, TaskKey.of(Work.DEVICE), () -> w.groups.create(player, members, source, name), id -> {
            if (state.panel() instanceof GroupEditor current && current.draft() == draft) {
                w.selectTarget(player, id);
                w.panel(player, new DevicePanel(id, DeviceSection.MEMBERS));
            }
            if (draft.pending != null) w.submit(player, new MusicSubmission(player.getUniqueId(), id, draft.pending.tracks(), draft.pending.placement()));
            else if (play) w.run(player, new TaskKey(Work.PLAYBACK, id), () -> w.devices.control(player, id, MusicPlaybackController.Action.RESUME), _ -> {});
        });
    }
    UiNode members(Player player, MusicWorkspaceState state, MusicState.Group group) {
        var owner = state.panel(); var position = state.sourcePosition;
        java.util.function.BooleanSupplier active = () -> w.active(player,state) && state.currentReading(owner,position);
        var body = column("group-members");
        var members = new ArrayList<UiNode>();
        var devices = w.registry.target(group.id()).members();
        var failures = w.devices.outputFailures(group.id());
        for (var device : devices) {
            var actions = new ArrayList<UiNode>();
            actions.add(w.button(player,"sound",() -> {
                if (active.getAsBoolean()) w.panel(player,new MemberOutput(group.id(),device.id(),state.sourcePosition));
            }));
            if (w.registry.mayControl(player,group.id())) actions.add(removal(player,state,group,device));
            var restriction = w.registry.controlFailure(player,device.id());
            Component status = failures.containsKey(device.id()) ? w.problem(player,failures.get(device.id()))
                    : restriction != null ? w.problem(player,restriction) : w.label(player,"available");
            members.add(MusicGroupContent.member(device.id(),w.locator.name(player,device),status,w.locator.position(player,device.id()),
                    w.locator.coordinates(player,device.id()),w.visitControl(player,device.id()),actions));
        }
        return body.child(MusicGroupContent.members(members,w.label(player,"empty"),position,next -> {
            if (active.getAsBoolean() && state.readPanel(owner,position,next)) w.redraw(player);
        })).build();
    }
    UiNode removal(Player player, MusicWorkspaceState state, MusicState.Group group, MusicDevice member) {
        String action = member == null || group.members().size() == 1 ? "dissolve-group" : "remove-member";
        UiKey anchor = UiKey.named(action + (member == null ? "" : ":" + member.id()));
        return Ui.button(w.label(player, action)).key(anchor).primary((_, handle) -> {
            String title = member == null ? group.name() : group.members().size() == 1
                    ? group.name() + " · " + w.registry.name(player, member.id()) : w.registry.name(player, member.id());
            var content = column("group-content-policy")
                    .child(info("target", w.raw(title))).child(info("effect", w.label(player, "removed-stopped")));
            for (var policy : MusicGroupService.ContentPolicy.values()) content.child(Ui.button(w.label(player,
                    policy == MusicGroupService.ContentPolicy.RESTORE_PREVIOUS ? "restore-previous" : "keep-group-content"))
                    .key(policy.name()).primary((_, popup) -> {
                        popup.closePopover();
                        w.run(player, new TaskKey(Work.DEVICE, group.id()), () -> member == null
                                ? w.groups.dissolve(player, group.id(), group.version(), policy)
                                : w.groups.removeMember(player, group.id(), group.version(), member.id(), policy), _ -> {
                            if (!w.registry.snapshot().groups().containsKey(group.id())) {
                                if (Objects.equals(state.target(), group.id())) w.selectTarget(player, null);
                                if (state.panel() instanceof DevicePanel panel && panel.target().equals(group.id())) w.panel(player, new NoPanel());
                            }
                        });
                    }).build());
            handle.openPopover(MenuPopover.attached(anchor, w.label(player, action), content.build(), w.label(player, "close")).exclusive());
        }).build();
    }
}
