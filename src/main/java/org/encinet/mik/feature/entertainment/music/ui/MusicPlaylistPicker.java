package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** Saving songs is a companion task; it never replaces the collection or queue underneath. */
final class MusicPlaylistPicker {
    private final MusicWorkspace w;
    MusicPlaylistPicker(MusicWorkspace workspace) { w = workspace; }

    UiElement button(Player player, MusicWorkspaceState state, List<MusicTrack> tracks, UiKey source) {
        return Ui.button(w.label(player, "add-playlist")).key(source).primary(action(player, state, tracks, source)).build();
    }

    FloatingMenuCommand action(Player player, MusicWorkspaceState state, List<MusicTrack> tracks, UiKey source) {
        var songs = List.copyOf(tracks);
        if (songs.isEmpty()) throw new IllegalArgumentException("No songs to save");
        return (_, handle) -> handle.openPopover(source, redraw -> panel(player, state, songs, source, redraw));
    }

    private MenuPanel panel(Player player, MusicWorkspaceState state, List<MusicTrack> songs, UiKey source, Runnable redraw) {
        return new MenuPanel() {
            private int page;
            private boolean active = true;
            private java.util.UUID lastSelection;
            @Override public MenuScene render() {
                if (!active) return MenuScene.screen("music-playlist-picker-closed").content(Ui.column().build()).build();
                var lists = w.playlists.owned(player.getUniqueId());
                var body = column("destinations");
                if (songs.size() == 1) body.child(info("song", w.raw(songs.getFirst().details().title())).withSummaryLines(2));
                else body.child(info("songs", w.language.text(player, Message.MUSIC_SPACE_SELECTED_TRACKS, NamedTextColor.AQUA, songs.size())));
                var rows = new ArrayList<MusicPlaylistTable.Row>();
                for (var list : lists) {
                    var existing = list.entries().stream().map(Playlist.Entry::trackId).collect(Collectors.toSet());
                    boolean present = songs.stream().allMatch(track -> existing.contains(track.id()));
                    var key = new TaskKey(Work.LIBRARY, list.id());
                    rows.add(new MusicPlaylistTable.Row(list, present, present || state.busy(key) ? w.label(player, present ? "already-added" : "saving") : null));
                }
                if (rows.isEmpty()) body.child(info("no-playlists", w.label(player, "no-playlists")));
                else body.child(MusicPlaylistTable.content(rows, key -> w.label(player, key), count -> w.trackCount(player, count),
                        list -> (_, _) -> {
                            if (!active) return;
                            lastSelection = list.id();
                            w.safe(player,() -> w.run(player,new TaskKey(Work.LIBRARY,list.id()),
                                    () -> w.playlists.add(player.getUniqueId(),list.id(),list.version(),songs),_ -> { if (active) redraw.run(); }));
                        },page,next -> { if (active) { page = next; redraw.run(); } }));
                if (lastSelection != null) body.children(w.taskStatus(player,state,new TaskKey(Work.LIBRARY,lastSelection)));
                var key = new TaskKey(Work.LIBRARY, player.getUniqueId());
                var create = Ui.button(w.label(player, "new-playlist")).key("new-playlist")
                        .primary((_, current) -> { if (active) current.openPopover(source,refresh -> create(player,state,songs,source,refresh)); });
                if (state.busy(key)) create.disabled(w.label(player, "saving"));
                body.child(create.build()).children(w.taskStatus(player, state, key));
                return MenuScene.screen("music-playlist-picker",w.label(player,"add-playlist"))
                        .content(UiTemplates.focus(body.build(),UiKey.named(rows.isEmpty() ? "no-playlists" : "playlists"))).build();
            }
            @Override public void close() { active = false; }
        };
    }

    private MenuPanel create(Player player, MusicWorkspaceState state, List<MusicTrack> tracks, UiKey source, Runnable redraw) {
        var form = MenuInputForm.builder(w.label(player, "new-playlist"))
                .field(MenuInputForm.Field.text("name", w.label(player, "name"), "", 64))
                .validate((_, values) -> {
                    try { Playlist.text(values.text("name"), 64); return null; }
                    catch (IllegalArgumentException invalid) { return w.problem(player, MusicProblem.Code.INVALID); }
                }).cancel(w.label(player, "cancel")).submit(w.label(player, "apply"), (_, values) -> w.safe(player,
                        () -> w.run(player, new TaskKey(Work.LIBRARY, player.getUniqueId()),
                                () -> w.playlists.create(player.getUniqueId(), values.text("name"), tracks), _ -> {}))).build();
        return new MusicInputPanel(player, form, "name", MenuFormSession.Actions.SUBMIT_AND_CANCEL, redraw,
                handle -> handle.openPopover(source, refresh -> panel(player, state, tracks, source, refresh)));
    }
}
