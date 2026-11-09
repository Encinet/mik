package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.column;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.info;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.tools;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.application.MusicRooms;
import org.encinet.mik.feature.entertainment.music.rhythm.*;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxRhythmReadiness;
import org.encinet.mik.shared.menu.ui.*;

/** Room discovery and personal preparation stay separate from playback device settings. */
final class MusicRhythmScenes {
    private final MusicWorkspace w;

    MusicRhythmScenes(MusicWorkspace workspace) { w = workspace; }

    UiNode picker(Player player, MusicWorkspaceState state) {
        var choices = w.rooms.nearby(player);
        var owner = state.panel(); var position = state.sourcePosition;
        BooleanSupplier active = () -> w.active(player, state) && state.currentReading(owner, position);
        var body = column("rhythm-picker").child(w.panelHeading(player, w.label(player, "rhythm")));
        if (choices.isEmpty()) return body.child(info("empty", w.label(player, "no-rhythm-devices"))).build();
        var rows = new ArrayList<MusicRhythmComposition.DeviceRow>();
        for (var choice : choices) {
            UUID id = choice.device().id();
            rows.add(new MusicRhythmComposition.DeviceRow(id, w.locator.name(player, choice.device()), w.locator.position(player, id),
                    w.locator.coordinates(player, id), choice.room() != null,
                    choice.restriction() == null ? null : w.problem(player, choice.restriction())));
        }
        return body.child(MusicRhythmComposition.directory(rows, key -> w.label(player, key), id -> (_, _) -> w.safe(player, () -> {
                    if (!active.getAsBoolean()) return;
                    w.rooms.open(player, id);
                    w.panel(player, new Room(id));
                }), position, next -> {
                    if (active.getAsBoolean() && state.readPanel(owner, position, next)) w.redraw(player);
                })).build();
    }

    UiNode room(Player player, MusicWorkspaceState state, Room panel) {
        UUID id = panel.device();
        var position = state.sourcePosition;
        BooleanSupplier active = () -> w.active(player, state) && state.currentReading(panel, position);
        MusicRooms.View room = w.rooms.get(id);
        if (room == null) return column("missing-room").child(w.panelHeading(player, w.label(player, "rhythm")))
                .child(info("reason", w.problem(player, MusicProblem.Code.NOT_FOUND)))
                .child(w.button(player, "back", () -> { if (active.getAsBoolean()) w.showRooms(player); })).build();
        var song = w.rooms.song(id);
        var readiness = w.rooms.readiness(id);
        var status = w.label(player, readiness == JukeboxRhythmReadiness.READY ? "room-score-ready"
                : readiness.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .color(readiness.playable() ? NamedTextColor.GREEN : NamedTextColor.AQUA);
        var summary = MusicRhythmComposition.summary(w.label(player, "current-track"),
                song == null ? w.label(player, "empty") : w.raw(song.details().title()), status);
        var players = new ArrayList<MusicRhythmComposition.Participant>();
        for (var member : room.members()) {
            var online = Bukkit.getPlayer(member.player());
            players.add(new MusicRhythmComposition.Participant(member.player(),
                    w.raw(online == null ? member.player().toString().substring(0, 8) : online.getName()),
                    w.label(player, member.playing() ? "room-playing" : member.ready() ? "ready" : "waiting")
                            .color(member.playing() ? NamedTextColor.AQUA : member.ready() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
        }
        var roster = column("room-roster").child(MusicRhythmComposition.participants(players, key -> w.label(player, key), position, next -> {
            if (active.getAsBoolean() && state.readPanel(panel, position, next)) w.redraw(player);
        })).build();
        var heading = w.panelHeading(player, w.raw(w.registry.name(player, id)));
        var me = room.member(player.getUniqueId());
        if (me == null) return MusicRhythmComposition.content(heading, summary, roster, null,
                List.of(w.button(player, "join-room", () -> { if (active.getAsBoolean()) { w.rooms.open(player, id); w.redraw(player); } })));
        boolean running = room.members().stream().anyMatch(RhythmRoom.Member::playing);
        UiNode preparation = me.playing() ? null : preparation(player, id, me, running, active);
        boolean controller = room.controller().equals(player.getUniqueId());
        var actions = new ArrayList<UiElement>();
        if (controller) actions.addAll(controls(player,state,room,readiness,running,active));
        actions.add(w.button(player,"leave-room",() -> {
            if (active.getAsBoolean()) { w.rooms.leave(player); w.panel(player,new NoPanel()); }
        }));
        if (controller) actions.add(w.button(player,"end-room",() -> { if (active.getAsBoolean()) w.confirm(player,new EndRoom(id)); }));
        return MusicRhythmComposition.content(heading,summary,roster,preparation,actions);
    }

    private UiNode preparation(Player player, UUID id, RhythmRoom.Member me, boolean running, BooleanSupplier active) {
        var mode = w.button(player, "room-mode", w.label(player, me.mode().name().toLowerCase(Locale.ROOT).replace('_', '-')), () -> {
            if (!active.getAsBoolean()) return;
            var current = member(player, id); var modes = RhythmGameMode.values();
            w.rooms.preference(player, id, modes[(current.mode().ordinal() + 1) % modes.length], current.difficulty()); w.redraw(player);
        });
        var difficulty = w.button(player, "room-difficulty", w.label(player, me.difficulty().name().toLowerCase(Locale.ROOT)), () -> {
            if (!active.getAsBoolean()) return;
            var current = member(player, id); var difficulties = RhythmDifficulty.values();
            w.rooms.preference(player, id, current.mode(), difficulties[(current.difficulty().ordinal() + 1) % difficulties.length]); w.redraw(player);
        });
        var participate = running ? w.button(player, "late-join", () -> { if (active.getAsBoolean()) w.rooms.lateJoin(player, id); })
                : w.choice(player, "ready", w.label(player, me.ready() ? "room-cancel-ready" : "room-ready"), me.ready(), () -> {
                    if (active.getAsBoolean()) { w.rooms.ready(player, id); w.redraw(player); }
                });
        return MusicRhythmComposition.preparation(key -> w.label(player, key), mode, difficulty,
                tools("room-personal-actions").child(w.button(player, "calibration", () -> { if (active.getAsBoolean()) w.rooms.calibrate(player, id); }).withSummaryLines(1)).child(participate.withSummaryLines(1)).build());
    }

    private RhythmRoom.Member member(Player player, UUID id) {
        var current = w.rooms.get(id);
        if (current == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
        var member = current.member(player.getUniqueId());
        if (member == null) throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
        return member;
    }

    private List<UiElement> controls(Player player, MusicWorkspaceState state, MusicRooms.View room, JukeboxRhythmReadiness readiness, boolean running, BooleanSupplier active) {
        UUID id = room.device();
        var selected = state.browser.selected(w.library.tracks(player, state));
        var choose = Ui.button(w.label(player, "select-room-track")).key("select-room-track");
        if (selected.size() == 1) choose.primary((_, _) -> w.safe(player, () -> {
            if (!active.getAsBoolean()) return;
            var current = state.browser.selected(w.library.tracks(player, state));
            if (current.size() == 1) w.rooms.select(player, id, current.getFirst());
            w.redraw(player);
        }));
        else choose.disabled(w.label(player, "room-select-one"));
        if (running) choose.disabled(w.label(player, "room-playing"));
        var controls = new ArrayList<UiElement>();
        controls.add(choose.build());
        if (readiness.playable() && room.members().stream().anyMatch(RhythmRoom.Member::ready))
            controls.add(w.button(player, "start-room", () -> { if (active.getAsBoolean()) w.rooms.start(player, id); }));
        return List.copyOf(controls);
    }
}
