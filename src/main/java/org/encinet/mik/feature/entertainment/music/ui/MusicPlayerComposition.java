package org.encinet.mik.feature.entertainment.music.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.ui.*;

/** Rearrange complete control groups when space is narrow; never wrap individual playback keys. */
final class MusicPlayerComposition {
    private MusicPlayerComposition() { }

    static UiNode content(UiNode context, UiNode playback, UiNode media, UiNode actions, UiNode feedback) {
        var body = Ui.column().key("player").gap(UiLength.ui(6)).child(context);
        body.child(Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(12)), UiLayouts.column(UiLength.ui(8))))
                .key("controls").child(playback).child(media).child(actions).build());
        if (feedback != null) body.child(feedback);
        return body.build();
    }

    static UiNode context(UiNode target, UiNode mode) {
        var row = Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(8)), UiLayouts.column(UiLength.ui(6)))).key("context").child(target);
        if (mode != null) row.child(mode);
        return row.build();
    }

    static UiNode playback(UiNode previous, UiNode primary, UiNode next) {
        return Ui.row().key("playback-keys").gap(UiLength.ui(4)).child(previous).child(primary).child(next).build();
    }

    static UiNode song(UiNode status, UiNode title, UiNode save) {
        var media = Ui.row().key("song-label").gap(UiLength.ui(6)).child(status).child(title).build();
        return Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(6)), UiLayouts.column(UiLength.ui(6))))
                .key("song").child(media).child(save).build();
    }

    static UiNode actions(UiNode volume, UiNode queue, UiNode more) {
        return Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(6)), UiLayouts.column(UiLength.ui(6))))
                .key("player-actions").child(volume).child(queue).child(more).build();
    }

    static UiNode key(String key, UiIcon icon, Component help, FloatingMenuCommand action, Component restriction) {
        var button = Ui.button(icon, help).key("player-" + key).primary(action);
        if (restriction != null) button.disabled(restriction);
        return button.build();
    }
}
