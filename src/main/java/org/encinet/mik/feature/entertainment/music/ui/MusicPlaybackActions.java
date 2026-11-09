package org.encinet.mik.feature.entertainment.music.ui;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiNode;
import org.encinet.mik.shared.menu.ui.UiElement;

/** The same two direct submission actions for song rows, collections and selections. */
final class MusicPlaybackActions {
    private MusicPlaybackActions() { }

    static String labelKey(PlaybackProgram.Submission placement) {
        return switch (placement) { case INSERT -> "insert"; case APPEND -> "append"; };
    }

    static UiNode buttons(List<MusicTrack> tracks, Function<String, Component> label,
                          BiConsumer<List<MusicTrack>, PlaybackProgram.Submission> submit) {
        var songs = List.copyOf(tracks);
        var actions = MusicWorkspace.tools("playback-actions");
        if (!songs.isEmpty()) for (var placement : PlaybackProgram.Submission.values()) {
            actions.child(button(songs, placement, label, submit));
        }
        return actions.build();
    }

    static UiElement button(List<MusicTrack> tracks, PlaybackProgram.Submission placement, Function<String, Component> label,
                         BiConsumer<List<MusicTrack>, PlaybackProgram.Submission> submit) {
        var songs = List.copyOf(tracks);
        String key = labelKey(placement);
        return Ui.button(label.apply(key)).key(key).primary((_, _) -> submit.accept(songs, placement)).build();
    }
}
