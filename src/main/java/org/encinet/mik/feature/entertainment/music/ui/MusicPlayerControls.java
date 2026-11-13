package org.encinet.mik.feature.entertainment.music.ui;

import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackSnapshot;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.shared.menu.ui.*;

/** One pure projection of playback facts; controls and status never infer each other's meaning. */
record MusicPlayerControls(Primary primary, boolean enabled, boolean previous, boolean next, UiIcon status, String statusLabel) {
    enum Primary {
        PLAY(UiIcons.PLAY, "play"), RESUME(UiIcons.PLAY, "resume"), PAUSE(UiIcons.PAUSE, "pause"),
        STOP(UiIcons.STOP,"stop"), CANCEL(UiIcons.CROSS, "cancel-loading");
        final UiIcon icon;
        final String label;
        Primary(UiIcon icon, String label) { this.icon = icon; this.label = label; }
    }

    static MusicPlayerControls sample(MusicPlaybackSnapshot snapshot, PlaybackProgram program, boolean busy) {
        boolean pending = !program.base().isEmpty() || !program.priority().isEmpty();
        var primary = switch (snapshot.status()) {
            case PLAYING -> snapshot.track() != null && snapshot.track().target() instanceof TrackTarget.NativeRecord ? Primary.STOP : Primary.PAUSE;
            case PAUSED -> Primary.RESUME;
            case LOADING -> Primary.CANCEL;
            case STOPPED, FINISHED -> Primary.PLAY;
        };
        boolean enabled = !busy && switch (primary) {
            case CANCEL -> snapshot.preparation() != null;
            case PAUSE, STOP -> snapshot.generation() != null;
            case RESUME -> snapshot.generation() != null || snapshot.entry() != null;
            case PLAY -> snapshot.entry() != null || pending;
        };
        UiIcon status = switch (snapshot.status()) {
            case PLAYING -> UiIcons.ACTIVITY_BARS;
            case PAUSED -> UiIcons.PAUSE;
            case LOADING -> UiIcons.LOADING;
            case STOPPED, FINISHED -> UiIcons.STOP;
        };
        return new MusicPlayerControls(primary, enabled, !busy && snapshot.entry() != null, !busy && pending,
                status, snapshot.status().name().toLowerCase(java.util.Locale.ROOT));
    }
}
