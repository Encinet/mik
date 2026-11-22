package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.column;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.info;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.tools;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackSnapshot;
import org.encinet.mik.feature.entertainment.music.lyrics.Lyrics;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricsService;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuWindowContext;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiElement;
import org.encinet.mik.shared.menu.ui.UiTemplates;

/** A window retains one playback revision, never reinterpreting a cue index in a new song. */
final class MusicLyricsPanel implements MenuPanel {
    private final MusicWorkspace w;
    private final Player player;
    private final MusicPlaybackSnapshot playback;
    private final LyricsService.Playback lyrics;
    private final MenuWindowContext<Void> window;
    private int track = -1;
    private int page;

    MusicLyricsPanel(MusicWorkspace w, Player player, MusicPlaybackSnapshot playback,
                     LyricsService.Playback lyrics, MenuWindowContext<Void> window) {
        this.w = w;
        this.player = player;
        this.playback = playback;
        this.lyrics = lyrics;
        this.window = window;
    }

    @Override public MenuScene render() {
        var body = column("lyrics-content").child(Ui.heading(w.raw(playback.track().details().title())).summaryLines(2).build());
        var result = state();
        switch (result) {
            case LyricsService.Available available -> show(body, available.document());
            case LyricsService.Status status -> body.child(info("lyrics-state", w.label(player, switch (status) {
                case PENDING -> "lyrics-pending";
                case EMPTY -> "lyrics-empty";
                case UNAVAILABLE -> "lyrics-unavailable";
            })));
            case null -> body.child(info("lyrics-state", w.label(player, "lyrics-ended")));
        }
        return MenuScene.screen("music-lyrics", w.label(player, "lyrics")).content(body.build())
                .refreshWhenChanged(10, _ -> state(), (_, _) -> window.redraw().run()).build();
    }

    private LyricsService.Lookup state() {
        return w.devices.lyrics(player, playback) == lyrics ? lyrics.poll() : null;
    }

    private void show(Ui.LinearBuilder body, Lyrics document) {
        if (track < 0) {
            var preference = List.of(new Locale.LanguageRange(player.locale().toLanguageTag().toLowerCase(Locale.ROOT)));
            track = document.choose(Lyrics.Role.ORIGINAL, preference).map(document.tracks()::indexOf).orElse(0);
        }
        var selected = document.tracks().get(track);
        var role = w.label(player, switch (selected.role()) {
            case ORIGINAL -> "lyrics-original";
            case TRANSLATION -> "lyrics-translation";
            case PRONUNCIATION -> "lyrics-pronunciation";
        });
        if (selected.language() != null) role = role.append(w.raw(" · " + selected.language()));
        boolean timed = !selected.cues().isEmpty();
        body.child(Ui.heading(role.append(w.raw(" · ")).append(w.label(player, timed ? "lyrics-timed" : "lyrics-plain"))).build());
        if (document.tracks().size() > 1) body.child(column("lyrics-tracks").child(UiTemplates.pager(new FloatingMenuPage(track, document.tracks().size(), 1), next -> {
            if (state() instanceof LyricsService.Available) { track = next; page = 0; window.redraw().run(); }
        })).build());
        var rows = new FloatingMenuPage(page, timed ? selected.cues().size() : selected.lines().size(), 6);
        for (int index = rows.fromIndex(); index < rows.toIndex(); index++) {
            Component line;
            if (timed) {
                var cue = selected.cues().get(index);
                line = w.raw(MusicTime.format(cue.startMs()) + "  " + cue.text());
            } else line = w.raw(selected.lines().get(index));
            body.child(Ui.text(line).key("lyric:" + index).summaryLines(3).textWidth(232).build());
        }
        if (rows.count() > 1) body.child(UiTemplates.pager(rows, next -> { page = next; window.redraw().run(); }));
        var selectedRole = w.devices.auxiliaryCaptions(player);
        body.child(Ui.heading(w.label(player, "lyrics-captions")).build());
        body.child(tools("lyric-captions")
                .child(captionChoice("lyrics-original-only", null, selectedRole))
                .child(captionChoice("lyrics-translation", Lyrics.Role.TRANSLATION, selectedRole))
                .child(captionChoice("lyrics-pronunciation", Lyrics.Role.PRONUNCIATION, selectedRole)).build());
    }

    private UiElement captionChoice(String key, Lyrics.Role role, Lyrics.Role current) {
        return w.choice(player, key, w.label(player, key), current == role, () -> {
            if (!(state() instanceof LyricsService.Available)) return;
            w.devices.auxiliaryCaptions(player, role);
            window.redraw().run();
        });
    }
}
