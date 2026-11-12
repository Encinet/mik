package org.encinet.mik.feature.world.plot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuInputForm;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** The notice's complete text stays in one draft until permissions and content pass final validation. */
final class PlotNoticeForm {
    private PlotNoticeForm() { }

    static MenuInputForm create(String initial, BiFunction<Message, Object[], Component> text,
                                Function<Player, Component> access, BiConsumer<Player, String> saved) {
        return MenuInputForm.builder(text.apply(Message.PLOT_NOTICE_BOARD, new Object[0]).color(NamedTextColor.GOLD))
                .field(MenuInputForm.Field.text("body", text.apply(Message.PLOT_NOTICE_INPUT,
                        new Object[] {PlotNoticeText.MAX_LENGTH}), initial, PlotNoticeText.MAX_LENGTH).multiline(true))
                .validate((player, values) -> {
                    Component denied = access.apply(player);
                    if (denied != null) return denied;
                    try { PlotNoticeText.normalize(values.text("body")); return null; }
                    catch (PlotProblem problem) { return text.apply(problem.message(), problem.args()).color(NamedTextColor.RED); }
                })
                .cancel(text.apply(Message.BACK, new Object[0]))
                .submit(text.apply(Message.PLOT_MENU_SUBMIT, new Object[0]).color(NamedTextColor.GREEN),
                        (player, values) -> saved.accept(player, PlotNoticeText.normalize(values.text("body"))))
                .build();
    }
}
