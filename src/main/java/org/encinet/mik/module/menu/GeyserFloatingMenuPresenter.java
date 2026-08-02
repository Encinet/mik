package org.encinet.mik.module.menu;

import org.bukkit.entity.Player;
import org.encinet.mik.module.geyser.BedrockSimpleForm;
import org.encinet.mik.module.geyser.GeyserService;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Renders the translated model with Bedrock's built-in SimpleForm UI. */
final class GeyserFloatingMenuPresenter {

    private final GeyserService geyser;
    private final LanguageService languageService;
    private final BedrockMenuTranslator translator = new BedrockMenuTranslator();

    GeyserFloatingMenuPresenter(GeyserService geyser,
                                LanguageService languageService) {
        this.geyser = Objects.requireNonNull(geyser, "geyser");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
    }

    boolean supports(Player player) {
        return geyser.isBedrockPlayer(player.getUniqueId());
    }

    boolean showMenu(Player player, FloatingMenuDefinition definition,
                     Consumer<BedrockMenuTranslator.Option> selected,
                     Runnable closed) {
        BedrockMenuTranslator.Menu model = translator.translate(definition, labels(player));
        List<String> buttons = model.options().stream()
                .map(BedrockMenuTranslator.Option::label)
                .toList();
        return geyser.sendForm(player.getUniqueId(), new BedrockSimpleForm(
                model.title(), model.content(), buttons,
                index -> {
                    if (index >= 0 && index < model.options().size()) {
                        selected.accept(model.options().get(index));
                    } else {
                        closed.run();
                    }
                }, closed));
    }

    boolean showActions(Player player, BedrockMenuTranslator.Option option,
                        Consumer<FloatingMenuInteraction> selected,
                        Runnable back) {
        BedrockMenuTranslator.ActionLabels labels = labels(player);
        List<String> buttons = new ArrayList<>();
        for (FloatingMenuInteraction interaction : option.interactions()) {
            buttons.add(labels.label(interaction));
        }
        buttons.add(languageService.t(player, Message.BEDROCK_MENU_BACK));
        return geyser.sendForm(player.getUniqueId(), new BedrockSimpleForm(
                BedrockMenuTranslator.firstLine(option.label()), option.label(), buttons,
                index -> {
                    if (index >= 0 && index < option.interactions().size()) {
                        selected.accept(option.interactions().get(index));
                    } else {
                        back.run();
                    }
                }, back));
    }

    void close(Player player) {
        geyser.closeForm(player.getUniqueId());
    }

    private BedrockMenuTranslator.ActionLabels labels(Player player) {
        return new BedrockMenuTranslator.ActionLabels(
                languageService.t(player, Message.BEDROCK_MENU_PRIMARY),
                languageService.t(player, Message.BEDROCK_MENU_SECONDARY),
                languageService.t(player, Message.BEDROCK_MENU_HOTKEY),
                languageService.t(player, Message.BEDROCK_MENU_SCROLL_UP),
                languageService.t(player, Message.BEDROCK_MENU_SCROLL_DOWN));
    }
}
