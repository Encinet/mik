package org.encinet.mik.shared.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;

import java.util.List;
import java.util.function.Consumer;

/** Native field editing, confirmations and external URL prompts. */
public final class MenuDialogs {
    private MenuDialogs() { }

    public static MenuDialogSession begin(JavaPlugin plugin, Player player) {
        return new MenuDialogSession(plugin, player);
    }

    /** Edits the complete source text. Completing this dialog does not persist a form's draft. */
    public static MenuDialogSession editText(JavaPlugin plugin, Player player, Component title, MenuInputForm.Field field,
                                            String value, Component problem, Component done, Component cancel,
                                            Consumer<String> accepted, Runnable cancelled) {
        var session = new MenuDialogSession(plugin, player, cancelled);
        // Client character units need not match the form's Unicode code-point limit.
        int clientLimit = (int) Math.min(Integer.MAX_VALUE, Math.max(value.length(), 2L * field.maximumLength()));
        var input = DialogInput.text("value", field.label()).width(360).initial(value).maxLength(clientLimit);
        if (field.multiline()) input.multiline(TextDialogInput.MultilineOptions.create(null, 160));
        var body = new java.util.ArrayList<DialogBody>();
        if (!field.label().equals(title)) body.add(DialogBody.plainMessage(field.label(), 360));
        if (problem != null) body.add(DialogBody.plainMessage(problem.color(NamedTextColor.RED), 360));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body)
                        .inputs(List.of(input.labelVisible(false).build())).build())
                .type(DialogType.confirmation(
                        ActionButton.create(done, null, 120, session.action((_, response) -> {
                            String text = response == null ? null : response.getText("value");
                            if (text == null) cancelled.run();
                            else accepted.accept(text);
                        })),
                        ActionButton.create(cancel, null, 100, session.action((_, _) -> cancelled.run())))));
        session.show(dialog);
        return session;
    }

    public static void openConfirm(JavaPlugin plugin, Player player, Component title,
                                   Component question, Component confirm, Component cancel,
                                   Consumer<Player> onConfirm) {
        MenuDialogSession session = begin(plugin, player);
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(question, 300))).build())
                .type(DialogType.confirmation(
                        ActionButton.create(confirm, null, 100,
                                session.action((responder, _) -> onConfirm.accept(responder))),
                        ActionButton.create(cancel, null, 90, session.cancelAction()))));
        session.show(dialog);
    }

    public static void openUrlConfirm(Player player, String label, String url, LanguageService languageService) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(languageService.t(player,
                                Message.URL_DIALOG_TITLE, label), org.encinet.mik.shared.menu.ui.UiPalette.DEFAULT.color(org.encinet.mik.shared.menu.ui.UiColorRole.WARNING)))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text()
                                        .append(Component.text(languageService.t(player, Message.URL_DIALOG_HINT), NamedTextColor.WHITE))
                                        .append(Component.newline())
                                        .append(languageService.rich(player, Message.URL_DIALOG_QUESTION_RICH, NamedTextColor.WHITE,
                                                RichArg.component("label", Component.text(label, NamedTextColor.YELLOW))))
                                        .append(Component.newline())
                                        .append(Component.text(url, NamedTextColor.AQUA))
                                        .build(), 280)))
                        .inputs(List.of())
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text(languageService.t(player, Message.URL_DIALOG_CONFIRM), NamedTextColor.GREEN),
                                Component.text(url, NamedTextColor.WHITE),
                                90,
                                DialogAction.staticAction(ClickEvent.openUrl(url))),
                        ActionButton.create(
                                Component.text(languageService.t(player, Message.BACK_TO_MAIN), NamedTextColor.WHITE),
                                null,
                                100,
                                DialogAction.staticAction(ClickEvent.runCommand("/menu")))
                )));
        player.showDialog(dialog);
    }
}
