package org.encinet.mik.feature.world.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.MenuInputForm;

/** Form drafts validate a detached candidate; only a guarded submission enters the real undo history. */
final class VehicleEditorForms {
    private VehicleEditorForms() { }
    static Component label(LanguageService language, Player player, String field) {
        return language.text(player, Message.VEHICLE_MODEL_FIELD, NamedTextColor.AQUA, field.replace('.', '-'));
    }
    static List<MenuInputForm.Field> vector(LanguageService language, Player player, VehicleVector value, String prefix) {
        var result = new ArrayList<MenuInputForm.Field>();
        double[] components = {value.coordinateX(), value.coordinateY(), value.coordinateZ()};
        for (int axis = 0; axis < 3; axis++) result.add(MenuInputForm.Field.text(prefix + "xyz".charAt(axis),
                label(language, player, prefix + "xyz".charAt(axis)), Double.toString(components[axis]), 64));
        return List.copyOf(result);
    }
    static VehicleVector vector(MenuInputForm.Values values, String prefix) {
        return VehicleModelDraft.vector(values.text(prefix + "x") + " " + values.text(prefix + "y") + " " + values.text(prefix + "z"));
    }
    static void open(Player player, LanguageService language, VehicleModelDraft draft, Component title, Component description,
                     List<MenuInputForm.Field> fields, Function<MenuInputForm.Values, Consumer<VehicleModelDraft>> edit,
                     Consumer<Player> guard, BiConsumer<Player, Runnable> attempt, Consumer<Player> changed) {
        var form = MenuInputForm.builder(title).description(description)
                .cancel(language.text(player, Message.CLOSE, NamedTextColor.AQUA));
        fields.forEach(form::field);
        form.validate((actor, values) -> {
            try { guard.accept(actor); draft.check(edit.apply(values)); return null; }
            catch (IllegalArgumentException | IllegalStateException exception) {
                return language.text(actor, Message.VEHICLE_ERROR, NamedTextColor.RED, exception.getMessage());
            }
        }).submit(language.text(player, Message.VEHICLE_MODEL_APPLY, NamedTextColor.AQUA), (actor, values) -> attempt.accept(actor, () -> {
            guard.accept(actor);
            edit.apply(values).accept(draft);
            changed.accept(actor);
        }));
        FloatingMenus.input(player, form.build());
    }
}
