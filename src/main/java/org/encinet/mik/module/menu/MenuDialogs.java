package org.encinet.mik.module.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;

import java.util.List;

public final class MenuDialogs {

    private MenuDialogs() {
    }

    public static void openUrlConfirm(Player player, String label, String url, LanguageService languageService) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(languageService.t(player, Message.URL_DIALOG_TITLE, label), MenuItems.TITLE_COLOR))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(
                                Component.text()
                                        .append(Component.text(languageService.t(player, Message.URL_DIALOG_HINT), NamedTextColor.GRAY))
                                        .append(Component.newline())
                                        .append(languageService.rich(player, Message.URL_DIALOG_QUESTION_RICH, NamedTextColor.GRAY,
                                                RichArg.component("label", Component.text(label, NamedTextColor.YELLOW), label)))
                                        .append(Component.newline())
                                        .append(Component.text(url, NamedTextColor.AQUA))
                                        .build(), 280)))
                        .inputs(List.of())
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text(languageService.t(player, Message.URL_DIALOG_CONFIRM), NamedTextColor.GREEN),
                                Component.text(url, NamedTextColor.GRAY),
                                90,
                                DialogAction.staticAction(ClickEvent.openUrl(url))),
                        ActionButton.create(
                                Component.text(languageService.t(player, Message.BACK_TO_MAIN), NamedTextColor.GRAY),
                                Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY),
                                100,
                                DialogAction.staticAction(ClickEvent.runCommand("/menu")))
                )));
        player.showDialog(dialog);
    }
}
