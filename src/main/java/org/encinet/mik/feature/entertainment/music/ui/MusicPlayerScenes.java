package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace.*;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.*;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;
import org.encinet.mik.shared.menu.ui.*;

/** Persistent player controls. Device discovery, membership and queue editing belong to their own scenes. */
final class MusicPlayerScenes implements AutoCloseable {
    private static final UiKey TARGET = UiKey.named("player-target"), MODE = UiKey.named("player-mode"),
            TIMELINE = UiKey.named("player-timeline"), SAVE = UiKey.named("player-save"),
            VOLUME = UiKey.named("player-volume"), MORE = UiKey.named("player-more");
    private final MusicWorkspace w;
    private final MusicPlayerPanels panels;
    private final Map<UUID, MusicPlayerTimeline> timelines = new HashMap<>();

    MusicPlayerScenes(MusicWorkspace workspace) { w = workspace; panels = new MusicPlayerPanels(workspace); }

    private MusicPlayerTimeline timeline(Player player, MusicWorkspaceState state) {
        return timelines.computeIfAbsent(player.getUniqueId(), _ -> new MusicPlayerTimeline(new MusicPlayerTimeline.Owner() {
            public boolean current(UUID target) { return w.owns(player, state) && target.equals(state.target()); }
            public Runnable later(Runnable action) {
                var task = Bukkit.getScheduler().runTaskLater(w.plugin, action, 2);
                return task::cancel;
            }
            public void seek(MusicPlaybackSnapshot expected, long position) {
                w.safe(player, () -> {
                    if (!w.devices.seek(player, expected, position)) throw new MusicProblem(MusicProblem.Code.UNSUPPORTED);
                });
            }
            public void refresh() { w.redraw(player); }
        }));
    }
    MusicPlaybackSnapshot sample(Player player, MusicWorkspaceState state) {
        var clock = timeline(player, state);
        var snapshot = state.target() == null ? null : w.devices.snapshot(state.target());
        clock.accept(snapshot);
        return snapshot;
    }

    private UiNode targetControl(Player player, MusicWorkspaceState state) {
        var targetLabel = state.target() == null ? w.label(player,"choose-targets")
                : w.language.text(player, org.encinet.mik.shared.i18n.Message.MUSIC_SPACE_PLAYBACK_TARGET,
                        net.kyori.adventure.text.format.NamedTextColor.WHITE,w.outputs.name(player,state.target()));
        return Ui.button(targetLabel).key(TARGET).summaryLines(1).textWidth(240)
                .primary((_, handle) -> handle.openPopover(TARGET, redraw -> w.outputs.picker(player, state, redraw))).build();
    }

    UiNode compact(Player player, MusicWorkspaceState state) {
        return MusicPlayerComposition.context(targetControl(player,state),null);
    }

    UiNode content(Player player, MusicWorkspaceState state) {
        var target = targetControl(player,state);
        if (state.focusedTask()) {
            release(player.getUniqueId());
            return MusicPlayerComposition.context(target, null);
        }
        if (state.target() == null) { sample(player, state); return MusicPlayerComposition.context(target, null); }
        try {
            var snapshot = sample(player, state);
            var clock = timeline(player, state);
            UUID id = snapshot.target();
            UiKey modeKey = source(MODE, id), volumeKey = source(VOLUME, id), saveKey = source(SAVE, id),
                    timelineKey = source(TIMELINE, snapshot), moreKey = source(MORE, snapshot);
            var program = w.devices.program(id);
            var selected = w.registry.playbackTarget(id);
            boolean online = selected.members().stream().anyMatch(w.registry::online);
            var restriction = w.registry.controlFailure(player, id);
            boolean editable = online && restriction == null;
            var key = new TaskKey(Work.PLAYBACK, id);
            boolean busy = state.busy(key);
            var controls = MusicPlayerControls.sample(snapshot, program, busy);
            Component reason = !online ? w.label(player, "offline") : restriction != null ? w.problem(player, restriction)
                    : busy ? w.label(player, "saving") : null;
            var mode = Ui.button(w.label(player, mode(program.mode()))).key(modeKey).summaryLines(1).textWidth(120)
                    .primary((_, handle) -> handle.openPopover(modeKey, _ -> panels.modes(player, state, id)));
            if (!editable) mode.disabled(reason);
            var context = MusicPlayerComposition.context(target, snapshot.entry() == null && snapshot.generation() != null ? null : mode.build());

            UiNode previous = playbackKey(player, state, snapshot, "previous", UiIcons.PREVIOUS, MusicPlaybackController.Action.PREVIOUS,
                    editable && controls.previous(), reason);
            var primary = MusicPlayerComposition.key("primary", controls.primary().icon, w.label(player, controls.primary().label),
                    (_, _) -> w.safe(player, () -> {
                        if (controls.primary() == MusicPlayerControls.Primary.CANCEL) {
                            if (!snapshot.target().equals(state.target()) || !w.devices.cancelPreparation(player, snapshot))
                                throw new MusicProblem(MusicProblem.Code.CONFLICT);
                            timeline(player, state).close();
                            w.redraw(player);
                        } else control(player, state, snapshot, controls.primary() == MusicPlayerControls.Primary.PAUSE
                                ? MusicPlaybackController.Action.PAUSE : MusicPlaybackController.Action.RESUME);
                    }), editable && controls.enabled() ? null : reason != null ? reason
                            : snapshot.status() == PlaybackStatus.LOADING ? w.label(player, "saving")
                            : snapshot.track() == null ? w.label(player, "empty") : w.problem(player, MusicProblem.Code.UNSUPPORTED));
            UiNode next = playbackKey(player, state, snapshot, "next", UiIcons.NEXT, MusicPlaybackController.Action.NEXT,
                    editable && controls.next(), reason);

            var media = column("media");
            var track = snapshot.track();
            if (track == null) media.child(info("no-song", w.label(player, "empty")));
            else {
                var status = Ui.icon(!online ? UiIcons.WARNING : controls.status(), !online ? w.label(player, "offline") : w.label(player, controls.statusLabel()))
                        .key("playback-status").tint(UiColor.themed(!online ? UiColorRole.WARNING : UiColorRole.MUSIC_ACCENT)).build();
                media.child(MusicPlayerComposition.song(status, Ui.text(w.raw(track.details().title())).key("song-title").summaryLines(1).textWidth(160).build(),
                        Ui.button(UiIcons.ADD, w.label(player, "add-playlist")).key(saveKey)
                                .primary(w.playlistPicker.action(player, state, List.of(track), saveKey)).build()));
                if (track.details().artist() != null && !track.details().artist().isBlank())
                    media.child(Ui.text(w.raw(track.details().artist())).key("artist").summaryLines(1).textWidth(160).build());
                if (snapshot.durationMillis() > 0) {
                    var range = UiRange.live(0, snapshot.durationMillis(), clock::position).step(5000)
                            .format(value -> w.raw(MusicTime.format((long) value))).withLabels();
                    var axis = editable && !busy && snapshot.seekable()
                            ? Ui.slider(w.label(player, "seek"), range, (_, _, value) -> w.safe(player, () -> clock.seek(snapshot, (long) value)))
                                .secondary((_, handle) -> handle.openPopover(timelineKey,
                                        redraw -> panels.preciseSeek(player, snapshot, clock, redraw)))
                                .tooltip(w.label(player, "timeline-help"))
                            : Ui.progress(w.label(player, "seek"), range);
                    media.child(axis.key(timelineKey).build());
                } else media.child(info("elapsed", w.raw(MusicTime.format(snapshot.positionMillis()) + " / —")));
            }

            var volume = Ui.button(UiIcons.VOLUME, w.label(player, "volume-control")).key(volumeKey)
                    .primary((_, handle) -> handle.openPopover(volumeKey, redraw -> panels.volume(player, state, id, volumeKey, redraw)));
            if (!editable) volume.disabled(reason);
            var queue = Ui.button(UiIcons.QUEUE, w.label(player, "device-queue")).key("player-queue").selected(
                    state.panel() instanceof DevicePanel panel && panel.target().equals(id) && panel.section() == DeviceSection.QUEUE
                    ).primary((_, _) -> w.panel(player, state.panel() instanceof DevicePanel panel && panel.target().equals(id) && panel.section() == DeviceSection.QUEUE
                            ? new NoPanel() : new DevicePanel(id, DeviceSection.QUEUE))).build();
            var more = Ui.button(UiIcons.MORE, w.label(player, "more")).key(moreKey)
                    .primary((_, handle) -> handle.openPopover(moreKey, _ -> panels.more(player, state, snapshot))).build();
            var actions = MusicPlayerComposition.actions(volume.build(), queue, more);

            var feedback = column("player-feedback");
            if (!online) feedback.child(info("offline", w.label(player, "offline")));
            else if (restriction != null) feedback.child(info("restricted", w.problem(player, restriction)));
            else if (track != null && !snapshot.seekable()) feedback.child(info("read-only", w.label(player, "timeline-read-only")));
            if (editable && state.lastSubmission != null && state.lastSubmission.target().equals(id) && state.lastSubmission.retryable())
                feedback.child(w.button(player, "retry-failed", () -> w.submit(player, state.lastSubmission)));
            else if (editable && !w.devices.outputFailures(id).isEmpty())
                feedback.child(w.button(player, "retry-outputs", () -> { w.devices.retryOutputs(player, id); w.redraw(player); }));
            feedback.children(w.taskStatus(player, state, key));
            var notices = feedback.build();
            return MusicPlayerComposition.content(context, MusicPlayerComposition.playback(previous, primary, next), media.build(), actions,
                    notices.children().isEmpty() ? null : notices);
        } catch (MusicProblem missing) {
            release(player.getUniqueId());
            return column("player-unavailable").child(target).child(info("reason", w.problem(player, missing.code()))).build();
        }
    }

    private UiNode playbackKey(Player player, MusicWorkspaceState state, MusicPlaybackSnapshot snapshot, String key, UiIcon icon,
                               MusicPlaybackController.Action action, boolean available, Component reason) {
        String label = action == MusicPlaybackController.Action.RESUME ? snapshot.status() == PlaybackStatus.PAUSED ? "resume" : "play"
                : action == MusicPlaybackController.Action.NEXT ? "skip-next" : action.name().toLowerCase(Locale.ROOT);
        // Equal icon cells keep the central action in place when availability or playback status changes.
        return MusicPlayerComposition.key(key, icon, w.label(player, label),
                (_, _) -> w.safe(player, () -> control(player, state, snapshot, action)),
                available ? null : reason != null ? reason : action == MusicPlaybackController.Action.NEXT
                        ? w.label(player, "queue-empty") : snapshot.track() == null ? w.label(player, "empty")
                        : w.problem(player, MusicProblem.Code.UNSUPPORTED));
    }
    void control(Player player, MusicWorkspaceState state, MusicPlaybackSnapshot expected, MusicPlaybackController.Action action) {
        if (!expected.target().equals(state.target()) || !expected.samePlayback(w.devices.snapshot(expected.target())))
            throw new MusicProblem(MusicProblem.Code.CONFLICT);
        state.lastSubmission = null;
        timeline(player, state).close();
        w.run(player, new TaskKey(Work.PLAYBACK, expected.target()), () -> w.devices.control(player, expected.target(), action), _ -> {});
    }
    static String mode(org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram.Mode mode) {
        return mode.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
    private static UiKey source(UiKey role, UUID target) { return UiKey.named(role.value() + ":" + target); }
    private static UiKey source(UiKey role, MusicPlaybackSnapshot playback) {
        return UiKey.named(source(role, playback.target()).value() + ":" + playback.entry() + ":" + playback.generation());
    }
    void release(UUID player) { var timeline = timelines.remove(player); if (timeline != null) timeline.close(); }
    @Override public void close() { timelines.values().forEach(MusicPlayerTimeline::close); timelines.clear(); }
}
