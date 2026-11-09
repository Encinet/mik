package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;

import java.util.*;
import java.util.stream.Collectors;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.playlist.*;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.ui.*;

/** The playlist stays visible while the author chooses one placement for a frozen selection. */
final class MusicPlaylistMoves {
    private final MusicWorkspace w;
    MusicPlaylistMoves(MusicWorkspace workspace) { w = workspace; }

    UiNode start(Player player, MusicWorkspaceState state, Playlist list, Set<UUID> selected) {
        if (selected.isEmpty()) return null;
        var order = list.entries().stream().map(Playlist.Entry::id).toList();
        var move = new MusicMove(list.id(), list.version(), order, selected);
        if (!move.hasDestination(Set.copyOf(order))) return null;
        long revision = state.browser.revision;
        var button = Ui.button(w.label(player, "move")).key("move").primary((_, _) -> w.safe(player, () -> {
            if (state.browser.revision != revision) throw new MusicProblem(MusicProblem.Code.CONFLICT);
            state.startPlaylistMove(move);
            w.redraw(player);
        }));
        if (state.busy(new TaskKey(Work.LIBRARY, list.id()))) button.disabled(w.label(player, "saving"));
        return button.build();
    }

    UiNode controls(Player player, MusicWorkspaceState state, Playlist list) {
        var move = state.browser.move;
        var cancel = Ui.button(w.label(player, "cancel-move")).key("cancel-move").primary((_, _) -> {
            if (state.browser.move == move) state.browser.move = null;
            w.redraw(player);
        });
        if (state.busy(new TaskKey(Work.LIBRARY, move.owner()))) cancel.disabled(w.label(player, "saving"));
        var controls = tools("placement-controls")
                .child(info("selected", w.language.text(player, Message.MUSIC_SPACE_SELECTED_TRACKS, NamedTextColor.AQUA, move.selected().size())))
                .child(info("placement", move.current(list.id(), list.version()) ? w.label(player, "move-hint") : w.problem(player, MusicProblem.Code.CONFLICT)))
                .child(cancel.build());
        return controls.build();
    }

    MusicTableSelection<MusicTrack> selection(Player player, MusicWorkspaceState state, Playlist list) {
        var move = state.browser.move;
        var ids = ids(list);
        var actions = new HashMap<UUID, FloatingMenuCommand>();
        return MusicTableSelection.moving(track -> move.selected().contains(ids.get(track.id())), track -> {
            UUID anchor = ids.get(track.id());
            return anchor == null ? null : actions.computeIfAbsent(anchor, id -> placement(player, state, list, move, EntryEdits.before(id)));
        });
    }

    UiNode end(Player player, MusicWorkspaceState state, Playlist list) {
        var action = placement(player, state, list, state.browser.move, EntryEdits.end());
        return action == null ? null : Ui.button(w.label(player, "move-end")).key("move-end").primary(action).build();
    }

    private FloatingMenuCommand placement(Player player, MusicWorkspaceState state, Playlist list, MusicMove move, EntryEdits.Destination destination) {
        var key = new TaskKey(Work.LIBRARY, move.owner());
        if (!list.author().equals(player.getUniqueId()) || !move.current(list.id(), list.version()) || state.busy(key) || !move.canPlace(destination)) return null;
        return (_, _) -> w.safe(player, () -> {
            if (state.browser.move != move) throw new MusicProblem(MusicProblem.Code.CONFLICT);
            w.run(player, key, () -> w.playlists.move(player.getUniqueId(), move.owner(), move.version(), move.selected(), destination), result -> {
                if (state.browser.move != move) return;
                state.browser.move = null;
                var tracks = w.library.tracks(player, state);
                var movedIds = ids(result);
                for (int i = 0; i < tracks.size(); i++) if (move.selected().contains(movedIds.get(tracks.get(i).id()))) {
                    state.browser.trackFocus = tracks.get(i).id();
                    break;
                }
            });
        });
    }
    private Map<String, UUID> ids(Playlist list) {
        return list.entries().stream().collect(Collectors.toMap(Playlist.Entry::trackId, Playlist.Entry::id));
    }
}
