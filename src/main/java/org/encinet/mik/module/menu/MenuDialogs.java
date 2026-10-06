package org.encinet.mik.module.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;

import java.util.List;
import java.time.Duration;
import java.util.function.BiConsumer;

public final class MenuDialogs {

    private MenuDialogs() {
    }

    /** Native text input for spatial-menu actions that need a short name or description. */
    public static void openTextInput(JavaPlugin plugin, Player player, Component title,
                                     Component label, String initial, int maximumLength,
                                     boolean multiline, Component submit, Component cancel,
                                     BiConsumer<Player, String> onSubmit) {
        openTextInput(plugin, player, title, label, initial, maximumLength,
                multiline ? TextDialogInput.MultilineOptions.create(8, 120) : null,
                submit, cancel, onSubmit);
    }

    public static void openTextInput(JavaPlugin plugin, Player player, Component title,
                                     Component label, String initial, int maximumLength,
                                     TextDialogInput.MultilineOptions multilineOptions,
                                     Component submit, Component cancel,
                                     BiConsumer<Player, String> onSubmit) {
        String inputKey = "value";
        var options = ClickCallback.Options.builder().uses(1)
                .lifetime(Duration.ofMinutes(5)).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of())
                        .inputs(List.of(DialogInput.text(inputKey, 300, label, true,
                                initial, maximumLength, multilineOptions)))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(submit, null, 100,
                                DialogAction.customClick((response, audience) -> {
                                    if (!(audience instanceof Player responder)
                                            || !responder.getUniqueId().equals(player.getUniqueId())) return;
                                    String value = response.getText(inputKey);
                                    Runnable action = () -> {
                                        if (responder.isOnline()) onSubmit.accept(responder,
                                                value == null ? "" : value.strip());
                                    };
                                    if (Bukkit.isPrimaryThread()) action.run();
                                    else Bukkit.getScheduler().runTask(plugin, action);
                                }, options)),
                        ActionButton.create(cancel, null, 90, null))));
        player.showDialog(dialog);
    }

    /** A compact in-game form for actions requiring three short text fields. */
    public static void openThreeTextInputs(JavaPlugin plugin, Player player, Component title,
                                           Component firstLabel, int firstMax,
                                           Component secondLabel, int secondMax,
                                           Component thirdLabel, int thirdMax,
                                           Component submit, Component cancel,
                                           BiConsumer<Player, ThreeTextValues> onSubmit) {
        var options = ClickCallback.Options.builder().uses(1)
                .lifetime(Duration.ofMinutes(5)).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of())
                        .inputs(List.of(
                                DialogInput.text("first", 300, firstLabel, true,
                                        "", firstMax, null),
                                DialogInput.text("second", 300, secondLabel, true,
                                        "", secondMax, null),
                                DialogInput.text("third", 300, thirdLabel, true,
                                        "", thirdMax, null)))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(submit, null, 100,
                                DialogAction.customClick((response, audience) -> {
                                    if (!(audience instanceof Player responder)
                                            || !responder.getUniqueId().equals(player.getUniqueId())) return;
                                    ThreeTextValues values = new ThreeTextValues(
                                            clean(response.getText("first")),
                                            clean(response.getText("second")),
                                            clean(response.getText("third")));
                                    Runnable action = () -> {
                                        if (responder.isOnline()) onSubmit.accept(responder, values);
                                    };
                                    if (Bukkit.isPrimaryThread()) action.run();
                                    else Bukkit.getScheduler().runTask(plugin, action);
                                }, options)),
                        ActionButton.create(cancel, null, 90, null))));
        player.showDialog(dialog);
    }

    public record ThreeTextValues(String first, String second, String third) { }

    /** Two prefilled fields for editing paired labels without a second dialog. */
    public static void openTwoTextInputs(JavaPlugin plugin, Player player, Component title,
                                         Component firstLabel, String firstInitial, int firstMax,
                                         Component secondLabel, String secondInitial, int secondMax,
                                         Component submit, Component cancel,
                                         BiConsumer<Player, TwoTextValues> onSubmit) {
        var options = ClickCallback.Options.builder().uses(1)
                .lifetime(Duration.ofMinutes(5)).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of())
                        .inputs(List.of(
                                DialogInput.text("first", 300, firstLabel, true,
                                        firstInitial, firstMax, null),
                                DialogInput.text("second", 300, secondLabel, true,
                                        secondInitial, secondMax, null)))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(submit, null, 100,
                                DialogAction.customClick((response, audience) -> {
                                    if (!(audience instanceof Player responder)
                                            || !responder.getUniqueId().equals(player.getUniqueId())) return;
                                    TwoTextValues values = new TwoTextValues(
                                            clean(response.getText("first")),
                                            clean(response.getText("second")));
                                    Runnable action = () -> {
                                        if (responder.isOnline()) onSubmit.accept(responder, values);
                                    };
                                    if (Bukkit.isPrimaryThread()) action.run();
                                    else Bukkit.getScheduler().runTask(plugin, action);
                                }, options)),
                        ActionButton.create(cancel, null, 90, null))));
        player.showDialog(dialog);
    }

    public record TwoTextValues(String first, String second) { }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    public static void openConfirm(JavaPlugin plugin, Player player, Component title,
                                   Component question, Component confirm, Component cancel,
                                   java.util.function.Consumer<Player> onConfirm) {
        var options = ClickCallback.Options.builder().uses(1)
                .lifetime(Duration.ofMinutes(5)).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(question, 300)))
                        .inputs(List.of()).build())
                .type(DialogType.confirmation(
                        ActionButton.create(confirm, null, 100,
                                DialogAction.customClick((response, audience) -> {
                                    if (!(audience instanceof Player responder)
                                            || !responder.getUniqueId().equals(player.getUniqueId())) return;
                                    Runnable action = () -> {
                                        if (responder.isOnline()) onConfirm.accept(responder);
                                    };
                                    if (Bukkit.isPrimaryThread()) action.run();
                                    else Bukkit.getScheduler().runTask(plugin, action);
                                }, options)),
                        ActionButton.create(cancel, null, 90, null))));
        player.showDialog(dialog);
    }

    public static void openUrlConfirm(Player player, String label, String url, LanguageService languageService) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(languageService.t(player,
                                Message.URL_DIALOG_TITLE, label), TextColor.color(0xC86A1D)))
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
                                null,
                                100,
                                DialogAction.staticAction(ClickEvent.runCommand("/menu")))
                )));
        player.showDialog(dialog);
    }
}
