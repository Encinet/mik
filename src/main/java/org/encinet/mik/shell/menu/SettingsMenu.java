package org.encinet.mik.shell.menu;

import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.player.security.PasswordModule;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.chat.ChatModule;
import org.encinet.mik.feature.player.profile.nametag.NameTagModule;
import org.encinet.mik.feature.player.pvp.PvpModule;
import org.encinet.mik.feature.player.teleport.TeleportPreferenceModule;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.LanguageChangeListener;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Owns category navigation; each selected feature continues to own its settings and drafts. */
public final class SettingsMenu implements AutoCloseable, LanguageChangeListener {
    private static final UiSize VIEWPORT = new UiSize(960, 2048);
    enum Category {
        SECURITY(Message.PASSWORD_TITLE),
        LANGUAGE(Message.MAIN_LANGUAGE),
        INTERFACE(Message.INTERFACE_SCALE_MENU_TITLE),
        CHAT(Message.CHAT_SETTINGS_MENU_TITLE),
        TELEPORT(Message.TELEPORT_MENU_TITLE),
        PVP(Message.PVP_MENU_TITLE),
        NAME_TAG(Message.MAIN_NAME_TAG);

        final Message label;
        Category(Message label) { this.label = label; }
        String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Invalidates the previous panel's redraw callbacks before releasing its input. */
    static final class Selection implements AutoCloseable {
        Category category = Category.INTERFACE;
        UiReadingPosition navigationPosition = new UiReadingPosition(UiKey.named("category:interface"),0,Category.INTERFACE.ordinal());
        MenuPanel panel;
        private Object token;

        boolean select(Category next, Function<Runnable, MenuPanel> create, Runnable redraw) {
            if (category == next && panel != null) return false;
            close();
            category = next;
            Object selected = new Object();
            token = selected;
            panel = create.apply(() -> { if (token == selected) redraw.run(); });
            return true;
        }

        @Override public void close() {
            token = null;
            MenuPanel previous = panel;
            panel = null;
            if (previous != null) previous.close();
        }
    }

    private final LanguageService language;
    private final Map<Category, BiFunction<Player, Runnable, MenuPanel>> panels;
    private final FloatingMenuScreen<Selection> screen = new FloatingMenuScreen<>("main-settings", _ -> new Selection(), this::render);

    public SettingsMenu(LanguageService language, LanguageMenu languages, ChatModule chat,
                        TeleportPreferenceModule teleports, PvpModule pvp, NameTagModule nameTags, PasswordModule security) {
        this.language = language;
        var panels = new EnumMap<Category, BiFunction<Player, Runnable, MenuPanel>>(Category.class);
        panels.put(Category.SECURITY, security::settingsPanel);
        panels.put(Category.LANGUAGE, languages::panel);
        panels.put(Category.INTERFACE, FloatingMenus::settingsPanel);
        panels.put(Category.CHAT, chat::settingsPanel);
        panels.put(Category.TELEPORT, teleports::settingsPanel);
        panels.put(Category.PVP, pvp::settingsPanel);
        panels.put(Category.NAME_TAG, nameTags::settingsPanel);
        this.panels = Map.copyOf(panels);
    }

    public void open(Player player) { screen.open(player); }

    public void enable() { language.addLanguageChangeListener(this); }

    @Override public void onLanguageChanged(Player player) {
        screen.flow(player).ifPresent(FloatingMenuFlow::redraw);
    }

    private MenuScene render(FloatingMenuContext<Selection> context) {
        Selection selected = context.state();
        Player player = context.player();
        selected.select(selected.category, redraw -> panels.get(selected.category).apply(player, redraw), context::redraw);
        MenuPanel owner = selected.panel;
        MenuScene panel = owner.render();
        UiNode navigation = navigation(message -> language.t(player, message), selected.category, selected.navigationPosition,
                position -> { selected.navigationPosition = position; context.redraw(); },
                category -> (viewer, handle) -> {
                    if (!selected.select(category, redraw -> panels.get(category).apply(viewer, redraw), context::redraw)) return;
                    selected.panel.attach(handle);
                    context.redraw();
                });
        return frame(panel, navigation).lifecycle(FloatingMenuLifecycle.combine((_, handle, previous, current, _) -> {
            if (current == FloatingMenuState.OPENING || current == FloatingMenuState.ACTIVE) owner.attach(handle);
            if (current == FloatingMenuState.CLOSING || current == FloatingMenuState.CLOSED) selected.close();
            else if (current == FloatingMenuState.OPENING && previous == FloatingMenuState.SUSPENDED) context.redraw();
        }, panel.lifecycle())).build();
    }

    static MenuScene.Builder frame(MenuScene panel, UiNode navigation) {
        return panel.toBuilder().screen("main-settings").withoutTitle().stableAnchor()
                .appearance(FloatingMenuAppearance.DEFAULT).viewport(VIEWPORT)
                .content(UiTemplates.workspace(navigation, panel.content()));
    }

    static UiNode navigation(Function<Message, String> text, Category selected, UiReadingPosition position, java.util.function.Consumer<UiReadingPosition> browse,
                             Function<Category, FloatingMenuCommand> select) {
        var choices = new java.util.ArrayList<UiNode>();
        for (Category category : Category.values()) choices.add(Ui.button(Component.text(text.apply(category.label),
                        category == selected ? NamedTextColor.GREEN : NamedTextColor.AQUA))
                .key("category:" + category.id()).selected(category == selected).role(UiRole.NAVIGATION)
                .keepAccessible().primary(select.apply(category)).build());
        return Ui.layout(UiLayouts.contentColumn(UiKey.named("choices"),UiLength.ui(16))).key("navigation")
                .child(Ui.heading(Component.text(text.apply(Message.MAIN_SETTINGS), NamedTextColor.LIGHT_PURPLE))
                        .key("settings-title").build())
                .child(UiKey.named("choices"), Ui.document().key("choices").gap(UiLength.ui(8)).blocks(choices).position(position,browse).build(), LinearSlot.stretch())
                .child(Ui.button(Component.text(text.apply(Message.BACK_TO_MAIN), NamedTextColor.GREEN))
                        .key("back-to-main").role(UiRole.NAVIGATION).keepAccessible()
                        .primary((_, handle) -> handle.back()).build()).build();
    }

    @Override public void close() {
        language.removeLanguageChangeListener(this);
        screen.closeWhere(_ -> true);
    }
}
