package org.encinet.mik.shared.menu.runtime.settings;

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
public final class FloatingMenuSettingsMenu {
    public record Actions(BiConsumer<Player, UnaryOperator<FloatingMenuPreferences>> change,
                   Consumer<Player> editLayout, Consumer<Player> editText,
                   Consumer<Player> editTheme, Consumer<Player> fieldOfView, Consumer<Player> reset) {
        public Actions {
            Objects.requireNonNull(change);
            Objects.requireNonNull(editLayout); Objects.requireNonNull(editText);
            Objects.requireNonNull(editTheme); Objects.requireNonNull(fieldOfView); Objects.requireNonNull(reset);
        }
    }
    private record PreferenceField(String key, Message label, UiNode control) { }
    private FloatingMenuSettingsMenu() { }

    static MenuScene definition(FloatingMenuPreferences selected, Function<Message,String> text, Actions actions, boolean standalone,
                                UiReadingPosition position, Consumer<UiReadingPosition> browse) {
        var theme = selected.theme();
        var sample = preview(selected,text);
        var motion = Ui.button(label(text,selected.reducedMotion() ? Message.CURRENT_ON : Message.CURRENT_OFF,theme,UiColorRole.TEXT))
                .key("reduced-motion").singleLine().selected(selected.reducedMotion())
                .tooltip(label(text,Message.INTERFACE_REDUCED_MOTION_HINT,theme,UiColorRole.TEXT))
                .primary((player,_) -> actions.change().accept(player,current -> current.withReducedMotion(!current.reducedMotion()))).build();
        var fields = List.of(
                new PreferenceField("layout",Message.INTERFACE_SCALE_LAYOUT_TITLE,options("layout",layoutOptions(selected,text,actions))),
                new PreferenceField("text",Message.INTERFACE_SCALE_TEXT_TITLE,options("text",textOptions(selected,text,actions))),
                new PreferenceField("theme",Message.INTERFACE_THEME_TITLE,options("theme",themeOptions(selected,text,actions))));
        var form = Ui.form().key("preferences");
        var sections = new ArrayList<UiNode>(fields.size() + 1);
        for (var field : fields) {
            var title = label(text,field.label(),theme,UiColorRole.ACCENT);
            form.field(field.key(),title,field.control());
            sections.add(Ui.form().key(field.key()).field(field.key(),title,field.control()).build());
        }
        var motionTitle = label(text,Message.INTERFACE_REDUCED_MOTION,theme,UiColorRole.ACCENT);
        var motionWithPreview = UiTemplates.actionRow(motion,sample);
        form.field("motion",motionTitle,standalone ? motion : motionWithPreview);
        sections.add(Ui.form().key("motion").field("motion",motionTitle,motionWithPreview).build());
        UiNode full = form.build();
        if (standalone) {
            var heading = Ui.heading(label(text,Message.INTERFACE_SCALE_MENU_TITLE,theme,UiColorRole.WARNING)).key("heading").singleLine().build();
            var sectionsWithPreview = Ui.column().key(full.key()).child(UiTemplates.actionRow(heading,sample)).child(full).build();
            full = UiTemplates.reading(sectionsWithPreview,full.key(),UiLength.ui(12));
        }
        var content = Ui.alternative(full,Ui.document().key("preferences").gap(UiLength.ui(4))
                .blocks(sections).position(position,browse).build());
        var tools = Ui.layout(UiLayouts.wrap(UiLength.ui(10),UiLength.ui(8))).key("navigation")
                .child(Ui.button(label(text,Message.INTERFACE_FOV_TITLE,theme,UiColorRole.ACCENT)
                                .append(Component.text(" " + selected.fieldOfView().degrees() + "°")))
                        .key("field-of-view").singleLine().tooltip(label(text,Message.INTERFACE_FOV_DESCRIPTION,theme,UiColorRole.TEXT))
                        .primary((player,_) -> actions.fieldOfView().accept(player)).build())
                .child(Ui.button(label(text,Message.INTERFACE_SCALE_RESET,theme,UiColorRole.WARNING)).key("reset-scale").singleLine()
                        .primary((player,_) -> actions.reset().accept(player)).build());
        if (standalone) tools.child(Ui.button(label(text,Message.BACK,theme,UiColorRole.SUCCESS)).key("back").singleLine()
                .role(UiRole.NAVIGATION).primary((_, handle) -> handle.back()).build());
        return MenuScene.screen("interface-scale").withoutTitle().stableAnchor()
                .content(UiTemplates.sheet(UiTemplates.window("settings",content),UiTemplates.toolbar("tools",tools.build()))).build();
    }

    private static UiNode options(String key, List<UiNode> items) {
        var columnGap = UiLength.ui(6);
        var rowGap = UiLength.ui(4);
        var layout = UiLayouts.adaptiveWrap(2, columnGap, rowGap,
                UiLayouts.grid(Math.ceilDiv(items.size(), 2), columnGap, rowGap));
        return Ui.layout(layout).key(key).children(items).build();
    }

    private static List<UiNode> layoutOptions(FloatingMenuPreferences selected, Function<Message,String> text, Actions actions) {
        var items = new ArrayList<UiNode>();
        var theme = selected.theme();
        BiConsumer<Player,Integer> adjust = (player,direction) -> actions.change()
                .accept(player,current -> current.withLayout(current.layout().step(direction)));
        for (var option : FloatingMenuScale.PRESETS) {
            boolean active = option.equals(selected.layout());
            items.add(wheel(Ui.button(optionLabel(option.percent(),active,theme))
                    .key("scale:" + option.percent()).singleLine().selected(active)
                    .tooltip(label(text,Message.INTERFACE_SCALE_ADJUST_HINT,theme,UiColorRole.TEXT))
                    .primary((player,_) -> actions.change().accept(player,current -> current.withLayout(option))),adjust).build());
        }
        items.add(customScale("layout-custom",selected.layout().percent(),!FloatingMenuScale.PRESETS.contains(selected.layout()),
                text,theme,actions.editLayout(),adjust));
        return List.copyOf(items);
    }

    private static List<UiNode> textOptions(FloatingMenuPreferences selected, Function<Message,String> text, Actions actions) {
        var items = new ArrayList<UiNode>();
        var theme = selected.theme();
        BiConsumer<Player,Integer> adjust = (player,direction) -> actions.change()
                .accept(player,current -> current.withText(current.text().step(direction)));
        for (var option : FloatingMenuTextScale.PRESETS) {
            boolean active = option.equals(selected.text());
            items.add(wheel(Ui.button(optionLabel(option.percent(),active,theme))
                    .key("text-scale:" + option.percent()).singleLine().selected(active)
                    .tooltip(label(text,Message.INTERFACE_SCALE_ADJUST_HINT,theme,UiColorRole.TEXT))
                    .primary((player,_) -> actions.change().accept(player,current -> current.withText(option))),adjust).build());
        }
        items.add(customScale("text-custom",selected.text().percent(),!FloatingMenuTextScale.PRESETS.contains(selected.text()),
                text,theme,actions.editText(),adjust));
        return List.copyOf(items);
    }

    private static UiNode customScale(String key, int percent, boolean active, Function<Message,String> text, UiThemeColors theme,
                                      Consumer<Player> edit, BiConsumer<Player,Integer> adjust) {
        var custom = label(text,Message.INTERFACE_SCALE_CUSTOM,theme,UiColorRole.ACCENT);
        return wheel(Ui.button(active ? optionLabel(percent,true,theme) : custom)
                .key(key).singleLine().selected(active).tooltip(custom)
                .primary((player,_) -> edit.accept(player)),adjust).build();
    }

    private static Ui.ElementBuilder wheel(Ui.ElementBuilder control, BiConsumer<Player,Integer> adjust) {
        return control.on(FloatingMenuInteraction.SCROLL_UP,(player,_,_) -> adjust.accept(player,1))
                .on(FloatingMenuInteraction.SCROLL_DOWN,(player,_,_) -> adjust.accept(player,-1));
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
        if (custom) items.add(Ui.button(UiIcons.SETTINGS,label(text,Message.INTERFACE_THEME_EDIT,theme,UiColorRole.ACCENT)).key("edit-theme")
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
                        .append(Component.text(" " + selected.fieldOfView().degrees() + "°"))).key("heading").singleLine()
                        .tooltip(label(text,Message.INTERFACE_FOV_DESCRIPTION,theme,UiColorRole.TEXT)).build())
                .child(body).build(),body.key());
        var controls = Ui.layout(UiLayouts.wrap(UiLength.ui(8),UiLength.ui(6))).key("adjustment")
                .child(fovWheel(Ui.button(Component.text("−5°",theme.color(UiColorRole.ACCENT))).key("fov-decrease")
                        .primary((player,_) -> change.accept(player,current -> current.withFieldOfView(current.fieldOfView().step(-1)))),change).build())
                .child(fovWheel(Ui.button(Component.text("FOV " + selected.fieldOfView().degrees() + "°",theme.color(UiColorRole.TEXT))
                                .append(Component.newline()).append(label(text,Message.INTERFACE_FOV_CUSTOM,theme,UiColorRole.TEXT)))
                        .key("fov-custom").summaryLines(2).primary((player,_) -> custom.accept(player)),change).build())
                .child(fovWheel(Ui.button(Component.text("+5°",theme.color(UiColorRole.ACCENT))).key("fov-increase")
                        .primary((player,_) -> change.accept(player,current -> current.withFieldOfView(current.fieldOfView().step(1)))),change).build())
                .child(Ui.button(label(text,Message.BACK,theme,UiColorRole.SUCCESS)).key("back").singleLine()
                        .primary((player,_) -> back.accept(player)).build()).build();
        return MenuScene.screen("interface-fov").withoutTitle().stableAnchor().content(UiTemplates.sheet(
                UiTemplates.window("field-of-view",content),UiTemplates.toolbar("tools",controls))).build();
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
