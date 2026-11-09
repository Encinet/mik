package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackController;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.playback.QueueEntry;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.ui.*;

/** Queue inspection and editing share occurrence identities, partitions and one frozen program version. */
final class MusicQueueScenes {
    private final MusicWorkspace w;
    MusicQueueScenes(MusicWorkspace workspace) { w = workspace; }

    UiNode content(Player player, MusicWorkspaceState state, UUID id) {
        w.registry.playbackTarget(id);
        var program = w.devices.program(id);
        var snapshot = w.queries.snapshot(player.getUniqueId());
        var body = column("queue");
        var failure = w.registry.controlFailure(player, id);
        boolean editable = failure == null;
        // An inserted disc can be audible while the digital queue keeps its own paused current entry.
        if (program.current() != null && !program.current().id().equals(w.devices.snapshot(id).entry())) {
            var current = snapshot.tracks().get(program.current().trackId());
            body.child(Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(6)), UiLayouts.column(UiLength.ui(6))))
                    .key("queue-current").child(Ui.text(w.label(player, "queue-current")).key("label").build())
                    .child(Ui.text(w.raw(current == null ? "—" : current.title())).key("song").summaryLines(1).textWidth(320).build()).build());
        }
        var rows = MusicQueueRows.of(program);
        var existing = rows.stream().map(row -> row.entry().id()).collect(java.util.stream.Collectors.toSet());
        state.queueSelection.retainAll(existing);
        var key = new TaskKey(Work.QUEUE, id);
        Set<UUID> chosen = Set.copyOf(state.queueSelection);
        if (rows.isEmpty()) body.child(info("empty", w.label(player, "queue-empty")));
        var move = state.queueMove;
        var candidates = program.movableEntries(chosen);
        var candidateIds = candidates.stream().map(QueueEntry::id).toList();
        MusicMove available = candidates.isEmpty() ? null : new MusicMove(id, program.version(), candidateIds, chosen);
        if (available != null && !available.hasDestination(existing)) available = null;
        var place = move == null ? null : placements(player, state, program, move, editable);
        var scope = state.panel();
        MusicTableSelection<MusicQueueTable.Row> interaction = move != null
                ? MusicTableSelection.moving(row -> move.selected().contains(row.entry().id()), row -> place.apply(EntryEdits.before(row.entry().id())))
                : editable ? MusicTableSelection.selecting(row -> chosen.contains(row.entry().id()), row -> (_, _) -> {
                    if (state.queueMove != null || state.panel() != scope
                            || w.devices.program(id).version() != program.version()) return;
                    UUID entry = row.entry().id();
                    if (!state.queueSelection.remove(entry)) state.queueSelection.add(entry);
                    w.redraw(player);
                }) : null;
        var table = rows.stream().map(row -> new MusicQueueTable.Row(row.entry(),snapshot.tracks().get(row.entry().trackId()),row.section())).toList();
        if (!table.isEmpty()) body.child(MusicQueueTable.content(table,interaction,label -> w.label(player,label),state.panelPage,
                page -> {
                    if (state.panel() != scope || w.devices.program(id).version() != program.version()) return;
                    state.panelPage = page; state.queueFocus = null; w.redraw(player);
                },state.queueFocus,program.mode() == PlaybackProgram.Mode.SHUFFLE));
        if (move != null) {
            var end = place.apply(EntryEdits.end());
            if (end != null) {
                var section = rows.stream().filter(row -> move.selected().contains(row.entry().id())).map(MusicQueueRows.Row::section).findFirst().orElseThrow();
                body.child(Ui.button(w.label(player,"move-end")).key("move-end")
                        .tooltip(w.label(player,section == MusicQueueRows.Section.REQUESTS ? "priority-queue" : "base-queue")).primary(end).build());
            }
        }
        if (move != null) return body.child(placementControls(player, state, program, move, failure)).children(w.taskStatus(player, state, key)).build();
        var actions = tools("queue-actions");
        if (editable && !chosen.isEmpty()) {
            actions.child(info("selected", w.language.text(player, Message.MUSIC_SPACE_SELECTED_TRACKS, NamedTextColor.AQUA, chosen.size())));
            var remove = Ui.button(w.label(player, "remove")).key("remove").primary((_, _) -> w.safe(player, () -> w.run(player, key,
                    () -> w.devices.removeQueue(player, id, program.version(), chosen), _ -> state.queueSelection.removeAll(chosen))));
            if (state.busy(key)) remove.disabled(w.label(player, "saving"));
            actions.child(remove.build())
                    .child(w.button(player, "clear-entries", () -> { state.queueSelection.clear(); w.redraw(player); }));
        }
        if (editable && available != null) {
            var draft = available;
            var start = Ui.button(w.label(player, "move")).key("move").primary((_, _) -> w.safe(player, () -> {
                state.startQueueMove(draft);
                w.redraw(player);
            }));
            if (state.busy(key)) start.disabled(w.label(player, "saving"));
            actions.child(start.build());
        }
        if (editable && w.devices.canUndo(player, id)) {
            var undo = Ui.button(w.label(player, "undo")).key("undo").primary((_, _) -> w.safe(player,
                    () -> w.run(player, key, () -> w.devices.undo(player, id), _ -> {})));
            if (state.busy(key)) undo.disabled(w.label(player, "saving"));
            actions.child(undo.build());
        }
        if (editable && !rows.isEmpty()) {
            var clear = Ui.button(w.label(player, "clear")).key("clear").primary((_, _) -> w.safe(player,
                    () -> w.run(player, key, () -> w.devices.control(player, id, MusicPlaybackController.Action.CLEAR), _ -> state.queueSelection.clear())));
            if (state.busy(key)) clear.disabled(w.label(player, "saving"));
            actions.child(clear.build());
        }
        if (program.current() != null && snapshot.tracks().containsKey(program.current().trackId())
                || rows.stream().anyMatch(row -> snapshot.tracks().containsKey(row.entry().trackId()))) {
            UiKey source = UiKey.named("save-queue:" + id);
            actions.child(Ui.button(w.label(player, "save-queue")).key(source)
                    .primary((viewer, handle) -> w.playlistPicker.action(player, state, MusicQueueRows.saveTracks(program, snapshot.tracks()), source)
                            .execute(viewer, handle)).build());
        }
        return body.child(actions.build()).children(w.taskStatus(player, state, key)).build();
    }

    private UiNode placementControls(Player player, MusicWorkspaceState state, PlaybackProgram program, MusicMove move, MusicProblem.Code failure) {
        var cancel = Ui.button(w.label(player, "cancel-move")).key("cancel-move").primary((_, _) -> {
            if (state.queueMove == move) state.queueMove = null;
            w.redraw(player);
        });
        if (state.busy(new TaskKey(Work.QUEUE, move.owner()))) cancel.disabled(w.label(player, "saving"));
        return tools("placement-controls")
                .child(info("selected", w.language.text(player, Message.MUSIC_SPACE_SELECTED_TRACKS, NamedTextColor.AQUA, move.selected().size())))
                .child(info("placement", failure != null ? w.problem(player, failure) : move.current(programTarget(state), program.version()) ? w.label(player, "move-hint") : w.problem(player, MusicProblem.Code.CONFLICT)))
                .child(cancel.build()).build();
    }
    private Function<EntryEdits.Destination, FloatingMenuCommand> placements(Player player, MusicWorkspaceState state,
            PlaybackProgram program, MusicMove move, boolean editable) {
        var key = new TaskKey(Work.QUEUE, move.owner());
        if (!move.current(programTarget(state), program.version()) || !editable || state.busy(key)) return _ -> null;
        var candidates = program.movableEntries(move.selected());
        if (candidates.isEmpty()) return _ -> null;
        var partition = candidates.stream().map(QueueEntry::id).collect(Collectors.toSet());
        var pending = MusicQueueRows.of(program).stream().map(row -> row.entry().id()).filter(partition::contains).collect(Collectors.toSet());
        return destination -> {
            if ((destination instanceof EntryEdits.Before before && !pending.contains(before.entry())) || !move.canPlace(destination)) return null;
            return (_, _) -> w.safe(player, () -> {
                if (state.queueMove != move) throw new MusicProblem(MusicProblem.Code.CONFLICT);
                w.run(player, key, () -> w.devices.moveQueue(player, move.owner(), move.version(), move.selected(), destination), _ -> {
                    if (state.queueMove != move) return;
                    state.queueMove = null;
                    var rows = MusicQueueRows.of(w.devices.program(move.owner()));
                    for (int i = 0; i < rows.size(); i++) if (move.selected().contains(rows.get(i).entry().id())) {
                        state.queueFocus = rows.get(i).entry().id();
                        break;
                    }
                });
            });
        };
    }
    private static UUID programTarget(MusicWorkspaceState state) {
        return state.panel() instanceof DevicePanel panel && panel.section() == DeviceSection.QUEUE ? panel.target() : null;
    }
}
