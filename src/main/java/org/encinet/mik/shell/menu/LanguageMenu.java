package org.encinet.mik.shell.menu;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.ui.*;
import java.util.function.IntConsumer;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;

import java.util.Objects;

/** A compact language catalog with native names and an explicit current selection. */
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
        screen.open(player, new MenuState(0));
    }

    static UiNode content(int page, IntConsumer browse) {
        var languages = UiTemplates.pagedActions("languages",
                UiLayouts.balancedGrid(4,1.3,UiLayouts.GridBias.WIDER,UiLength.ui(10),UiLength.ui(8)),page,browse);
        var body = UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(languages.key(),UiLength.ui(12))).key("catalogue")
                .child(UiTemplates.title("reader")).child(languages).build(), languages.key());
        return UiTemplates.sheet(UiTemplates.window("languages", body),
                UiTemplates.toolbar("tools", UiTemplates.navigation("navigation")));
    }

    private MenuScene build(FloatingMenuContext<MenuState> context) {
        Player player = context.player();
        MenuScene content = definition(player, context.state().page(),
                page -> context.setState(new MenuState(page)), context::redraw, () -> screen.state(player).orElse(null) == context.state());
        return content.toBuilder().content(UiTemplates.withBack(content.content(),
                Component.text(languageService.t(player, context.canGoBack() ? Message.BACK : Message.CLOSE),
                        NamedTextColor.GREEN))).build();
    }

    MenuPanel panel(Player player, Runnable redraw) {
        return new MenuPanel() {
            private int page;
            private boolean closed;
            @Override public MenuScene render() {
                if (closed) return MenuScene.screen("language-closed").content(Ui.column().build()).build();
                return definition(player, page, next -> { if (!closed) { page = next; redraw.run(); } },
                        () -> { if (!closed) redraw.run(); }, () -> !closed);
            }
            @Override public void close() { closed = true; }
        };
    }

    private MenuScene definition(Player player, int page, IntConsumer browse, Runnable redraw, java.util.function.BooleanSupplier active) {
        Language displayLanguage = languageService.language(player);
        String currentPreference = languageService.preference(player.getUniqueId());
        Language[] languages = Language.values();

        MenuScene.Builder menu = MenuScene.screen("language")
                .appearance(FloatingMenuAppearance.DEFAULT)
                .stableAnchor()
                .content(content(page, browse));
        Component reader = Component.text(languageService.t(displayLanguage,
                        Message.LANGUAGE_MENU_TITLE), NamedTextColor.LIGHT_PURPLE)
                .append(Component.newline())
                .append(Component.text(displayLanguage.displayName(), NamedTextColor.GOLD));
        if (displayLanguage.id().equals(currentPreference)) {
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
            menu.control(CHOICE_PREFIX + value, choiceLabel(player, value, selected)).selected(selected)
                    .region("languages")
                    .textWidth(FloatingMenuTextWidth.COMPACT)
                    .primary((p, _) -> {
                        if (!active.getAsBoolean()) return;
                        selectLanguage(p, value);
                        redraw.run();
                    });
        }
        boolean automatic = LanguageService.AUTO.equals(currentPreference);
        menu.control("automatic", choiceLabel(player, LanguageService.AUTO, automatic)).selected(automatic)
                .region("navigation")
                .textWidth(FloatingMenuTextWidth.COMPACT)
                .keepAccessible()
                .primary((p, _) -> {
                    if (!active.getAsBoolean()) return;
                    selectLanguage(p, LanguageService.AUTO);
                    redraw.run();
                });
        return menu.build();
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

    record MenuState(int page) { }
}
