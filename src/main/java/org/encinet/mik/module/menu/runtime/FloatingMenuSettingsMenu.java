package org.encinet.mik.module.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPreferences;
import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuFieldOfView;
import org.encinet.mik.module.menu.FloatingMenuInteraction;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

final class FloatingMenuSettingsMenu {
    private FloatingMenuSettingsMenu() { }

    static FloatingMenuDefinition definition(FloatingMenuPreferences selected, boolean spatial,
                                             Function<Message, String> text,
                                             BiConsumer<Player, FloatingMenuScale> selectLayout,
                                             BiConsumer<Player, FloatingMenuTextScale> selectText,
                                             Consumer<Player> fieldOfView,
                                             Consumer<Player> reset) {
        var menu = FloatingMenuDefinition.screen("interface-scale",
                        Component.text(text.apply(Message.INTERFACE_SCALE_MENU_TITLE), NamedTextColor.GOLD))
                .stableAnchor().frontArc().framing(FloatingMenuFraming.WIDE_ARC)
                .layout(FloatingMenuLayouts.panoramicPanels("summary", 0.4,
                        FloatingMenuLayouts.panel("layout", FloatingMenuLayouts.menu(
                                FloatingMenuLayouts.heading("layout-group-heading"),
                                FloatingMenuLayouts.actions("layout", 3)), "layout-group-heading", "layout"),
                        FloatingMenuLayouts.panel("summary", FloatingMenuLayouts.menu(
                                FloatingMenuLayouts.information("description"),
                                FloatingMenuLayouts.information("preview"),
                                FloatingMenuLayouts.information("native-hint"),
                                FloatingMenuLayouts.navigation("navigation")),
                                "description", "preview", "native-hint", "navigation"),
                        FloatingMenuLayouts.panel("text", FloatingMenuLayouts.menu(
                                FloatingMenuLayouts.heading("text-group-heading"),
                                FloatingMenuLayouts.actions("text", 3)), "text-group-heading", "text")));
        menu.information("description", Component.text(text.apply(Message.INTERFACE_SCALE_DESCRIPTION), NamedTextColor.GRAY))
                .region("description");
        menu.information("layout-heading", Component.text(text.apply(Message.INTERFACE_SCALE_LAYOUT_TITLE)
                        + " " + selected.layout().percent() + "%", NamedTextColor.AQUA)).region("layout-group-heading");
        menu.information("text-heading", Component.text(text.apply(Message.INTERFACE_SCALE_TEXT_TITLE)
                        + " " + selected.text().percent() + "%", NamedTextColor.AQUA)).region("text-group-heading");
        for (FloatingMenuScale option : FloatingMenuScale.values()) {
            var control = menu.control("scale:" + option.id(), optionLabel(option.percent(), option == selected.layout()))
                    .selected(option == selected.layout()).region("layout")
                    .primary((player, handle) -> selectLayout.accept(player, option));
            if (spatial) control.scrollUp((player, handle) -> selectLayout.accept(player, selected.layout().step(1)))
                    .scrollDown((player, handle) -> selectLayout.accept(player, selected.layout().step(-1)));
        }
        for (FloatingMenuTextScale option : FloatingMenuTextScale.values()) {
            var control = menu.control("text-scale:" + option.id(), optionLabel(option.percent(), option == selected.text()))
                    .selected(option == selected.text()).region("text")
                    .primary((player, handle) -> selectText.accept(player, option));
            if (spatial) control.scrollUp((player, handle) -> selectText.accept(player, selected.text().step(1)))
                    .scrollDown((player, handle) -> selectText.accept(player, selected.text().step(-1)));
        }
        menu.information("preview", Component.text(text.apply(Message.INTERFACE_SCALE_PREVIEW), NamedTextColor.WHITE))
                .region("preview");
        if (!spatial)
            menu.information("native-hint", Component.text(text.apply(Message.INTERFACE_SCALE_NATIVE_HINT), NamedTextColor.GRAY))
                    .region("native-hint");
        if (spatial)
            menu.control("field-of-view", Component.text(text.apply(Message.INTERFACE_FOV_TITLE)
                            + " " + selected.fieldOfView().degrees() + "°", NamedTextColor.AQUA))
                    .region("navigation").primary((player, handle) -> fieldOfView.accept(player));
        menu.control("reset-scale", Component.text(text.apply(Message.INTERFACE_SCALE_RESET), NamedTextColor.YELLOW))
                .region("navigation").primary((player, handle) -> reset.accept(player));
        menu.back(Component.text(text.apply(Message.BACK_TO_MAIN), NamedTextColor.GREEN)).region("navigation");
        return menu.build();
    }

    static FloatingMenuDefinition fieldOfViewDefinition(FloatingMenuFieldOfView selected,
                                                        Function<Message, String> text,
                                                        BiConsumer<Player, FloatingMenuFieldOfView> select,
                                                        Consumer<Player> custom, Consumer<Player> back) {
        var menu = FloatingMenuDefinition.screen("interface-fov",
                        Component.text(text.apply(Message.INTERFACE_FOV_TITLE), NamedTextColor.GOLD))
                .stableAnchor().frontArc()
                .layout(FloatingMenuLayouts.menu(FloatingMenuLayouts.information("description"),
                        FloatingMenuLayouts.actions("presets", 3), FloatingMenuLayouts.actions("adjustment", 3),
                        FloatingMenuLayouts.navigation("navigation")));
        menu.information("description", Component.text(text.apply(Message.INTERFACE_FOV_DESCRIPTION), NamedTextColor.GRAY))
                .region("description");
        for (FloatingMenuFieldOfView option : FloatingMenuFieldOfView.PRESETS)
            menu.control("fov:" + option.degrees(), Component.text(option.degrees() + "°",
                            option.equals(selected) ? NamedTextColor.GREEN : NamedTextColor.WHITE))
                    .selected(option.equals(selected)).region("presets")
                    .primary((player, handle) -> select.accept(player, option));
        menu.control("fov-decrease", Component.text("−5°", NamedTextColor.AQUA)).region("adjustment")
                .primary((player, handle) -> select.accept(player, selected.step(-1)));
        menu.control("fov-custom", Component.text("FOV " + selected.degrees() + "°", NamedTextColor.WHITE)
                        .append(Component.newline()).append(Component.text(text.apply(Message.INTERFACE_FOV_CUSTOM), NamedTextColor.GRAY)))
                .region("adjustment").primary((player, handle) -> custom.accept(player));
        menu.control("fov-increase", Component.text("+5°", NamedTextColor.AQUA)).region("adjustment")
                .primary((player, handle) -> select.accept(player, selected.step(1)));
        menu.on(FloatingMenuInteraction.SCROLL_UP,
                (player, handle, interaction) -> select.accept(player, selected.step(1)));
        menu.on(FloatingMenuInteraction.SCROLL_DOWN,
                (player, handle, interaction) -> select.accept(player, selected.step(-1)));
        menu.control("back", Component.text(text.apply(Message.BACK), NamedTextColor.GREEN)).region("navigation")
                .primary((player, handle) -> back.accept(player));
        return menu.build();
    }

    private static Component optionLabel(int percent, boolean selected) {
        return Component.text(percent + "%",
                selected ? NamedTextColor.GREEN : NamedTextColor.WHITE);
    }
}
