package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.*;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.*;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.feature.entertainment.music.ui.MusicSoundEdits.Parameter;

/** Small, managed companions leave the collection and queue in place. */
final class MusicPlayerPanels {
    private final MusicWorkspace w;
    MusicPlayerPanels(MusicWorkspace workspace) { w = workspace; }

    MenuPanel modes(Player player, MusicWorkspaceState state, UUID id) {
        return () -> {
            var current = w.devices.program(id).mode();
            var key = new TaskKey(Work.QUEUE, id);
            var failure = w.registry.controlFailure(player, id);
            var body = column("playback-mode");
            for (var mode : PlaybackProgram.Mode.values()) {
                var choice = Ui.button(w.label(player, MusicPlayerScenes.mode(mode))).key(MusicPlayerScenes.mode(mode)).selected(current == mode)
                        .primary((_, handle) -> {
                            handle.closePopover();
                            w.safe(player, () -> w.run(player, key, () -> w.devices.mode(player, id, mode), _ -> {}));
                        });
                if (failure != null) choice.disabled(w.problem(player, failure));
                else if (state.busy(key)) choice.disabled(w.label(player, "saving"));
                body.child(choice.build());
            }
            return scene("mode", w.label(player, "mode"), body.build());
        };
    }

    MenuPanel volume(Player player, MusicWorkspaceState state, UUID id, UiKey source, Runnable redraw) {
        return () -> {
            var group = w.registry.snapshot().groups().get(id);
            boolean grouped = group != null;
            var key = new TaskKey(Work.DEVICE, id);
            var parameter = grouped ? Parameter.GAIN : Parameter.VOLUME;
            var field = w.sound.control(player, id, parameter, UiKey.named("volume-value"), redraw,
                    (_, handle) -> handle.openPopover(source, refresh -> w.sound.precise(player, id, parameter, refresh,
                            current -> current.openPopover(source, update -> volume(player, state, id, source, update)))));
            var body = column("volume").child(field).children(w.taskStatus(player, state, key));
            return scene("volume", w.label(player, grouped ? "group-volume" : "volume-control"), body.build());
        };
    }

    MenuPanel more(Player player, MusicWorkspaceState state, MusicPlaybackSnapshot snapshot) {
        return () -> {
            var body = column("player-more");
            if (w.registry.mayControl(player, snapshot.target()) && snapshot.track() != null && snapshot.status() != org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus.STOPPED
                    && snapshot.status() != org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus.FINISHED)
                body.child(Ui.button(w.label(player, "stop")).key("stop")
                    .primary((_, handle) -> { handle.closePopover(); w.safe(player,
                            () -> w.transport.control(player, state, snapshot, MusicPlaybackController.Action.STOP)); }).build());
            body.child(Ui.button(w.label(player, "device-settings")).key("device-settings").primary((_, handle) -> {
                handle.closePopover();
                w.panel(player, new DevicePanel(snapshot.target(), DeviceSection.SETTINGS));
            }).build());
            return scene("more", w.label(player, "more"), body.build());
        };
    }

    MenuPanel preciseSeek(Player player, MusicPlaybackSnapshot expected, MusicPlayerTimeline timeline, Runnable redraw) {
        var form = MenuInputForm.builder(w.label(player, "seek"))
                .description(w.label(player, "seek-format"))
                .field(MenuInputForm.Field.text("position", w.label(player, "seek"), MusicTime.format((long) timeline.position()), 20))
                .validate((_, values) -> {
                    try {
                        long position = MusicTime.parse(values.text("position"));
                        if (position >= expected.durationMillis()) return w.problem(player, MusicProblem.Code.INVALID);
                        if (!expected.samePlayback(w.devices.snapshot(expected.target()))) return w.problem(player, MusicProblem.Code.CONFLICT);
                        return null;
                    } catch (IllegalArgumentException | ArithmeticException invalid) { return w.problem(player, MusicProblem.Code.INVALID); }
                }).cancel(w.label(player, "cancel")).submit(w.label(player, "apply"),
                        (_, values) -> w.safe(player, () -> timeline.seek(expected, MusicTime.parse(values.text("position"))))).build();
        return new MusicInputPanel(player, form, "position", MenuFormSession.Actions.SUBMIT, redraw, FloatingMenuHandle::closePopover);
    }

    private static MenuScene scene(String key, net.kyori.adventure.text.Component title, UiNode content) {
        return MenuScene.screen("music-player-" + key, title).content(content).build();
    }
}
