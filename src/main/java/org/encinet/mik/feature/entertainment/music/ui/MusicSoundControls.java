package org.encinet.mik.feature.entertainment.music.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.feature.entertainment.music.ui.MusicSoundEdits.Parameter;
import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;

/** Device settings and playback companions use the same numeric model, precise editor and write queue. */
final class MusicSoundControls implements AutoCloseable {
    private record Target(UUID player, UUID device) { }
    private final MusicWorkspace w;
    private final Map<Target, MusicSoundEdits> edits = new HashMap<>();

    MusicSoundControls(MusicWorkspace workspace) { w = workspace; }

    UiNode fields(Player player, UUID id, Parameter... parameters) {
        var fields = Ui.form().key("sound-settings");
        for (var parameter : parameters) {
            var source = UiKey.named("sound:" + id + ":" + parameter.key);
            fields.field(parameter.key, w.label(player, parameter.key),
                    control(player, id, parameter, source, () -> w.redraw(player), (_, handle) ->
                            handle.openPopover(source, redraw -> precise(player, id, parameter, redraw, FloatingMenuHandle::closePopover))));
        }
        return fields.build();
    }

    UiNode control(Player player, UUID id, Parameter parameter, UiKey key, Runnable redraw, FloatingMenuCommand edit) {
        var pending = edits(player, id);
        var range = UiRange.live(parameter.minimum, parameter.maximum, () -> pending.value(parameter)).step(parameter.step)
                .format(value -> w.language.text(player, Message.MUSIC_SPACE_SOUND_VALUE, NamedTextColor.WHITE,
                        parameter == Parameter.RANGE ? "range" : "volume", (int) Math.round(value)));
        var field = Ui.numberField(w.label(player, parameter.key), range,
                (viewer, _, value) -> change(viewer, id, parameter, (int) Math.round(value), redraw)).key(key).edit(edit);
        var failure = w.registry.controlFailure(player, id);
        if (failure != null) field.disabled(w.problem(player, failure));
        else if (w.state(player).busy(new TaskKey(Work.DEVICE, id)) && !pending.active()) field.disabled(w.label(player, "saving"));
        return field.build();
    }

    MenuPanel precise(Player player, UUID id, Parameter parameter, Runnable redraw, Consumer<FloatingMenuHandle> finished) {
        var form = MenuInputForm.builder(w.label(player, parameter.key))
                .field(MenuInputForm.Field.text("value", w.label(player, parameter.key), Integer.toString(edits(player, id).value(parameter)), 3))
                .validate((viewer, values) -> {
                    try { parameter.parse(values.text("value")); return null; }
                    catch (IllegalArgumentException invalid) { return w.problem(viewer, MusicProblem.Code.INVALID); }
                }).cancel(w.label(player, "cancel")).submit(w.label(player, "apply"),
                        (viewer, values) -> change(viewer, id, parameter, parameter.parse(values.text("value")), redraw)).build();
        return new MusicInputPanel(player, form, "value", MenuFormSession.Actions.SUBMIT_AND_CANCEL, redraw, finished);
    }

    private void change(Player player, UUID id, Parameter parameter, int value, Runnable redraw) {
        w.safe(player, () -> {
            var pending = edits(player, id);
            if (pending.active()) pending.change(parameter, value);
            else {
                var key = new TaskKey(Work.DEVICE, id);
                if (w.state(player).busy(key)) throw new MusicProblem(MusicProblem.Code.BUSY);
                w.run(player, key, () -> pending.change(parameter, value), _ -> redraw.run());
            }
            redraw.run();
        });
    }

    private MusicSoundEdits edits(Player player, UUID id) {
        return edits.computeIfAbsent(new Target(player.getUniqueId(), id), _ -> new MusicSoundEdits(parameter -> {
            if (parameter == Parameter.GAIN) {
                var group = w.registry.snapshot().groups().get(id);
                if (group == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
                return group.gain();
            }
            var device = w.registry.snapshot().devices().get(id);
            if (device == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
            return parameter == Parameter.VOLUME ? device.volume() : device.range();
        }, changes -> changes.containsKey(Parameter.GAIN) ? w.groups.groupGain(player, id, changes.get(Parameter.GAIN))
                : w.devices.sound(player, id, changes.get(Parameter.VOLUME), changes.get(Parameter.RANGE)), w::main));
    }

    void forget(UUID player) {
        edits.entrySet().removeIf(entry -> {
            if (!entry.getKey().player().equals(player)) return false;
            entry.getValue().close();
            return true;
        });
    }
    @Override public void close() { edits.values().forEach(MusicSoundEdits::close); edits.clear(); }
}
