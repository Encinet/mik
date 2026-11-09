package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** All everyday preferences share one form; numeric controls keep their own wheel actions. */
final class FloatingMenuSettingsMenu {
    record Actions(BiConsumer<Player, UnaryOperator<FloatingMenuPreferences>> change,
                   Consumer<Player> editTheme, Consumer<Player> fieldOfView, Consumer<Player> reset) {
        Actions {
            Objects.requireNonNull(change);
            Objects.requireNonNull(editTheme); Objects.requireNonNull(fieldOfView); Objects.requireNonNull(reset);
        }
    }
    private FloatingMenuSettingsMenu() { }

    static MenuScene definition(FloatingMenuPreferences selected, Function<Message,String> text, Actions actions) {
        var theme = selected.theme();
        var choices = Ui.form().key("preferences")
                .field("layout",label(text,Message.INTERFACE_SCALE_LAYOUT_TITLE,theme,UiColorRole.ACCENT),
                        options("layout",layoutOptions(selected,text,actions)))
                .field("text",label(text,Message.INTERFACE_SCALE_TEXT_TITLE,theme,UiColorRole.ACCENT),
                        options("text",textOptions(selected,text,actions)))
                .field("theme",label(text,Message.INTERFACE_THEME_TITLE,theme,UiColorRole.ACCENT),
                        options("theme",themeOptions(selected,text,actions)))
                .field("motion",label(text,Message.INTERFACE_REDUCED_MOTION,theme,UiColorRole.ACCENT),
                        Ui.button(label(text,selected.reducedMotion() ? Message.CURRENT_ON : Message.CURRENT_OFF,theme,UiColorRole.TEXT))
                                .key("reduced-motion").singleLine().selected(selected.reducedMotion())
                                .tooltip(label(text,Message.INTERFACE_REDUCED_MOTION_HINT,theme,UiColorRole.TEXT))
                                .primary((player,_) -> actions.change().accept(player,current -> current.withReducedMotion(!current.reducedMotion()))).build())
                .build();
        var content = UiTemplates.reading(choices.key(),
                Ui.heading(label(text,Message.INTERFACE_SCALE_MENU_TITLE,theme,UiColorRole.WARNING)).key("heading").singleLine().build(),
                choices,preview(selected,text));
        var tools = Ui.layout(UiLayouts.wrap(UiLength.ui(10),UiLength.ui(8))).key("navigation")
                .child(Ui.button(label(text,Message.INTERFACE_FOV_TITLE,theme,UiColorRole.ACCENT)
                                .append(Component.text(" " + selected.fieldOfView().degrees() + "°")))
                        .key("field-of-view").tooltip(label(text,Message.INTERFACE_FOV_DESCRIPTION,theme,UiColorRole.TEXT))
                        .primary((player,_) -> actions.fieldOfView().accept(player)).build())
                .child(Ui.button(label(text,Message.INTERFACE_SCALE_RESET,theme,UiColorRole.WARNING)).key("reset-scale")
                        .primary((player,_) -> actions.reset().accept(player)).build()).build();
        return MenuScene.screen("interface-scale").withoutTitle().stableAnchor()
                .content(UiTemplates.sheet(UiTemplates.window("settings",content),UiTemplates.toolbar("tools",tools))).build();
    }

    private static UiNode options(String key, List<UiNode> items) {
        return Ui.layout(UiLayouts.wrap(UiLength.ui(8),UiLength.ui(6))).key(key).children(items).build();
    }

    private static List<UiNode> layoutOptions(FloatingMenuPreferences selected, Function<Message,String> text, Actions actions) {
        var items = new ArrayList<UiNode>();
        var theme = selected.theme();
        for (var option : FloatingMenuScale.values()) items.add(Ui.button(optionLabel(option.percent(),option == selected.layout(),theme))
                .key("scale:" + option.id()).singleLine().selected(option == selected.layout())
                .tooltip(label(text,Message.INTERFACE_SCALE_ADJUST_HINT,theme,UiColorRole.TEXT))
                .primary((player,_) -> actions.change().accept(player,current -> current.withLayout(option)))
                .on(FloatingMenuInteraction.SCROLL_UP,(player,_,_) -> actions.change().accept(player,current -> current.withLayout(current.layout().step(1))))
                .on(FloatingMenuInteraction.SCROLL_DOWN,(player,_,_) -> actions.change().accept(player,current -> current.withLayout(current.layout().step(-1)))).build());
        return List.copyOf(items);
    }

    private static List<UiNode> textOptions(FloatingMenuPreferences selected, Function<Message,String> text, Actions actions) {
        var items = new ArrayList<UiNode>();
        var theme = selected.theme();
        for (var option : FloatingMenuTextScale.values()) items.add(Ui.button(optionLabel(option.percent(),option == selected.text(),theme))
                .key("text-scale:" + option.id()).singleLine().selected(option == selected.text())
                .tooltip(label(text,Message.INTERFACE_SCALE_ADJUST_HINT,theme,UiColorRole.TEXT))
                .primary((player,_) -> actions.change().accept(player,current -> current.withText(option)))
                .on(FloatingMenuInteraction.SCROLL_UP,(player,_,_) -> actions.change().accept(player,current -> current.withText(current.text().step(1))))
                .on(FloatingMenuInteraction.SCROLL_DOWN,(player,_,_) -> actions.change().accept(player,current -> current.withText(current.text().step(-1)))).build());
        return List.copyOf(items);
    }

    private static List<UiNode> themeOptions(FloatingMenuPreferences selected, Function<Message,String> text, Actions actions) {
        var items = new ArrayList<UiNode>();
        var theme = selected.theme();
        for (var palette : UiPalette.values()) {
            var title = switch (palette) {
                case CHARCOAL -> Message.INTERFACE_THEME_CHARCOAL;
                case WARM -> Message.INTERFACE_THEME_WARM;
                case FOREST -> Message.INTERFACE_THEME_FOREST;
            };
            items.add(Ui.button(Component.text((palette == theme ? "✓ " : "") + text.apply(title),theme.color(UiColorRole.TEXT)))
                    .key("theme:" + palette.id()).summaryLines(2).selected(palette == theme)
                    .tooltip(label(text,Message.INTERFACE_THEME_HINT,theme,UiColorRole.TEXT))
                    .primary((player,_) -> actions.change().accept(player,current -> current.withTheme(palette))).build());
        }
        boolean custom = theme instanceof UiCustomTheme;
        items.add(Ui.button(Component.text((custom ? "✓ " : "") + text.apply(Message.INTERFACE_THEME_CUSTOM),theme.color(UiColorRole.TEXT)))
                .key("theme:custom").summaryLines(2).selected(custom)
                .tooltip(label(text,Message.INTERFACE_THEME_CUSTOM_HINT,theme,UiColorRole.TEXT))
                .primary((player,_) -> actions.change().accept(player,FloatingMenuPreferences::customizeTheme)).build());
        if (custom) items.add(Ui.button(label(text,Message.INTERFACE_THEME_EDIT,theme,UiColorRole.ACCENT)).key("edit-theme").summaryLines(2)
                .primary((player,_) -> actions.editTheme().accept(player)).build());
        return List.copyOf(items);
    }

    static MenuScene fieldOfViewDefinition(FloatingMenuPreferences selected, Function<Message,String> text, int page, IntConsumer browse,
                                           BiConsumer<Player,UnaryOperator<FloatingMenuPreferences>> change,
                                           Consumer<Player> custom, Consumer<Player> back) {
        var theme = selected.theme();
        var presets = new ArrayList<UiNode>();
        for (var option : FloatingMenuFieldOfView.PRESETS) presets.add(fovWheel(Ui.button(Component.text(option.degrees() + "°",
                                option.equals(selected.fieldOfView()) ? theme.color(UiColorRole.SUCCESS) : theme.color(UiColorRole.TEXT)))
                        .key("fov:" + option.degrees()).singleLine().selected(option.equals(selected.fieldOfView()))
                        .primary((player,_) -> change.accept(player,current -> current.withFieldOfView(option))),change).build());
        var body = Ui.pagedFlow(2).key("presets").items(presets).page(page,browse).build();
        var content = UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(UiKey.named("presets"),UiLength.ui(10))).key("fov-content")
                .child(Ui.heading(label(text,Message.INTERFACE_FOV_TITLE,theme,UiColorRole.WARNING)
                        .append(Component.text(" " + selected.fieldOfView().degrees() + "°"))).key("heading").singleLine().build())
                .child(Ui.text(label(text,Message.INTERFACE_FOV_DESCRIPTION,theme,UiColorRole.TEXT)).key("description").textWidth(240).build())
                .child(body).build(),body.key());
        var controls = Ui.layout(UiLayouts.wrap(UiLength.ui(8),UiLength.ui(6))).key("adjustment")
                .child(fovWheel(Ui.button(Component.text("−5°",theme.color(UiColorRole.ACCENT))).key("fov-decrease")
                        .primary((player,_) -> change.accept(player,current -> current.withFieldOfView(current.fieldOfView().step(-1)))),change).build())
                .child(fovWheel(Ui.button(Component.text("FOV " + selected.fieldOfView().degrees() + "°",theme.color(UiColorRole.TEXT))
                                .append(Component.newline()).append(label(text,Message.INTERFACE_FOV_CUSTOM,theme,UiColorRole.TEXT)))
                        .key("fov-custom").summaryLines(2).primary((player,_) -> custom.accept(player)),change).build())
                .child(fovWheel(Ui.button(Component.text("+5°",theme.color(UiColorRole.ACCENT))).key("fov-increase")
                        .primary((player,_) -> change.accept(player,current -> current.withFieldOfView(current.fieldOfView().step(1)))),change).build()).build();
        return MenuScene.screen("interface-fov").withoutTitle().stableAnchor().content(UiTemplates.sheet(
                UiTemplates.window("field-of-view",content),UiTemplates.toolbar("tools",UiTemplates.menu(controls,
                        Ui.button(label(text,Message.BACK,theme,UiColorRole.SUCCESS)).key("back").primary((player,_) -> back.accept(player)).build())))).build();
    }

    private static Ui.ElementBuilder fovWheel(Ui.ElementBuilder control, BiConsumer<Player,UnaryOperator<FloatingMenuPreferences>> change) {
        return control.on(FloatingMenuInteraction.SCROLL_UP,(player,_,_) -> change.accept(player,current -> current.withFieldOfView(current.fieldOfView().step(1))))
                .on(FloatingMenuInteraction.SCROLL_DOWN,(player,_,_) -> change.accept(player,current -> current.withFieldOfView(current.fieldOfView().step(-1))));
    }

    private static UiNode preview(FloatingMenuPreferences selected, Function<Message,String> text) {
        var theme = selected.theme();
        int accent = theme.argb(UiColorRole.ACCENT), warning = theme.argb(UiColorRole.WARNING), white = theme.argb(UiColorRole.TEXT);
        var colors = Ui.grid(2).gap(UiLength.ZERO).child(swatch(accent)).child(swatch(warning))
                .child(swatch(white)).child(swatch(white & 0x55ffffff)).build();
        return Ui.row().key("preview-content").gap(UiLength.ui(12))
                .child(Ui.group(colors).key("preview-colors").transform(UiTransform.IDENTITY.rotated(-10,8,-4)).build())
                .child(Ui.text(label(text,Message.INTERFACE_SCALE_PREVIEW,theme,UiColorRole.TEXT)).key("preview").singleLine().build()).build();
    }
    private static UiNode swatch(int argb) {
        return Ui.surface(Ui.stack().build()).size(new UiSize(16,16)).style(UiStyle.PLAIN.withBackground(UiColor.fromArgb(argb))).build();
    }
    private static Component label(Function<Message,String> text, Message key, UiThemeColors theme, UiColorRole role) {
        return Component.text(text.apply(key),theme.color(role));
    }
    private static Component optionLabel(int percent, boolean selected, UiThemeColors theme) {
        return Component.text(percent + "%",theme.color(selected ? UiColorRole.SUCCESS : UiColorRole.TEXT));
    }
}
