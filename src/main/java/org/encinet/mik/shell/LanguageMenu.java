package org.encinet.mik.shell;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuContext;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;

import java.util.Objects;

/** A player-centered ring of language choices with one clear reading point. */
public final class LanguageMenu {
    private static final String CHOICE_PREFIX = "language:";

    private final LanguageService languageService;
    private final FloatingMenuScreen<MenuState> screen;

    public LanguageMenu(LanguageService languageService) {
        this.languageService = Objects.requireNonNull(languageService, "languageService");
        this.screen = new FloatingMenuScreen<>("language", this::build);
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("lang")
                        .executes(ctx -> {
                            if (ctx.getSource().getSender() instanceof Player player) {
                                open(player);
                            } else {
                                ctx.getSource().getSender().sendMessage(Component.text(
                                        languageService.t(Language.DEFAULT, Message.PLAYER_ONLY),
                                        NamedTextColor.RED));
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(), languageService.t(Language.DEFAULT,
                        Message.LANGUAGE_COMMAND_DESCRIPTION)));
    }

    public void open(Player player) {
        String preference = languageService.preference(player.getUniqueId());
        int focused = Language.fromId(preference)
                .orElseGet(() -> languageService.language(player)).ordinal();
        screen.open(player, new MenuState(focused));
    }

    private FloatingMenuDefinition build(FloatingMenuContext<MenuState> context) {
        Player player = context.player();
        Language displayLanguage = languageService.language(player);
        String currentPreference = languageService.preference(player.getUniqueId());
        Language[] languages = Language.values();
        int focused = Math.floorMod(context.state().rotation(), languages.length);
        Language focusedLanguage = languages[focused];

        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("language")
                .appearance(FloatingMenuAppearance.CONSOLE)
                .stableAnchor()
                .aroundViewer()
                .layout(new LanguageRingLayout(context.state().rotation()));
        Component reader = Component.text(languageService.t(displayLanguage,
                        Message.LANGUAGE_MENU_TITLE), NamedTextColor.LIGHT_PURPLE)
                .append(Component.newline())
                .append(Component.text(focusedLanguage.displayName(), NamedTextColor.GOLD));
        if (focusedLanguage.id().equals(currentPreference)) {
            reader = reader.append(Component.newline()).append(Component.text(
                    languageService.t(displayLanguage, Message.LANGUAGE_SELECTED),
                    NamedTextColor.GREEN));
        }
        menu.information("language-heading", reader).region("reader")
                .textWidth(FloatingMenuTextWidth.WIDE)
                .keepAccessible();

        for (int languageIndex = 0; languageIndex < languages.length; languageIndex++) {
            Language option = languages[languageIndex];
            String value = option.id();
            boolean selected = value.equals(currentPreference);
            int selectedIndex = languageIndex;
            menu.choice(CHOICE_PREFIX + value, selected,
                            languageMaterial(option), Component.text(option.displayName(),
                                    selected ? NamedTextColor.GREEN : NamedTextColor.AQUA))
                    .region(LanguageRingLayout.cardRegion(languageIndex))
                    .textWidth(FloatingMenuTextWidth.RING)
                    .primary((p, handle) -> {
                        selectLanguage(p, value);
                        context.update(state -> focus(state, selectedIndex));
                    });
        }
        menu.on(FloatingMenuInteraction.SCROLL_UP, (p, handle, input) ->
                context.update(value -> rotate(value, -1)));
        menu.on(FloatingMenuInteraction.SCROLL_DOWN, (p, handle, input) ->
                context.update(value -> rotate(value, 1)));
        boolean automatic = LanguageService.AUTO.equals(currentPreference);
        menu.choice("automatic", automatic, Material.COMPASS,
                        choiceLabel(player, LanguageService.AUTO, automatic))
                .region("navigation")
                .textWidth(FloatingMenuTextWidth.RING)
                .keepAccessible()
                .primary((p, handle) -> {
                    selectLanguage(p, LanguageService.AUTO);
                    context.update(state -> focus(state, languageService.language(p).ordinal()));
                });
        menu.back(Component.text(languageService.t(displayLanguage,
                        context.canGoBack() ? Message.BACK : Message.CLOSE),
                        NamedTextColor.GREEN))
                .region("navigation")
                .keepAccessible();
        return menu.build();
    }

    static MenuState rotate(MenuState state, int direction) {
        return new MenuState(state.rotation() + Integer.signum(direction));
    }

    static MenuState focus(MenuState state, int selected) {
        int count = Language.values().length;
        int focused = Math.floorMod(state.rotation(), count);
        int difference = Math.floorMod(selected - focused + count / 2, count) - count / 2;
        return new MenuState(state.rotation() + difference);
    }

    private Component choiceLabel(Player player, String value, boolean selected) {
        Component label = Component.text(languageService.languageLabel(player, value),
                selected ? NamedTextColor.GREEN : NamedTextColor.AQUA);
        return selected ? label.append(Component.newline()).append(Component.text(
                languageService.t(player, Message.LANGUAGE_SELECTED),
                NamedTextColor.GREEN)) : label;
    }

    private void selectLanguage(Player player, String value) {
        languageService.setPreference(player.getUniqueId(), value);
        player.sendMessage(Component.text(languageService.t(player, Message.LANGUAGE_SET,
                languageService.languageLabel(player, value)), NamedTextColor.GREEN));
    }

    private static Material languageMaterial(Language language) {
        return switch (language) {
            case ZH_CN -> Material.RED_BANNER;
            case ZH_HK -> Material.MAGENTA_BANNER;
            case ZH_TW -> Material.PINK_BANNER;
            case LZH -> Material.BLACK_BANNER;
            case EN_US -> Material.BLUE_BANNER;
            case DE_DE -> Material.YELLOW_BANNER;
            case ES_ES -> Material.ORANGE_BANNER;
            case FR_FR -> Material.WHITE_BANNER;
            case IT_IT -> Material.LIME_BANNER;
            case JA_JP -> Material.RED_BANNER;
            case KO_KR -> Material.LIGHT_BLUE_BANNER;
            case NL_NL -> Material.ORANGE_BANNER;
            case PT_BR -> Material.GREEN_BANNER;
            case RU_RU -> Material.CYAN_BANNER;
            case TH_TH -> Material.PURPLE_BANNER;
            case UK_UA -> Material.YELLOW_BANNER;
        };
    }

    record MenuState(long rotation) { }
}
