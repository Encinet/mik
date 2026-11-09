package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Edits independent snapshot values through the shared native field form. */
final class FloatingMenuThemeEditor {
    private static final List<UiColorRole> ROLES = List.of(UiColorRole.WINDOW, UiColorRole.SIDEBAR, UiColorRole.TOOLBAR,
            UiColorRole.INPUT, UiColorRole.TOOLTIP, UiColorRole.BUTTON, UiColorRole.BUTTON_HOVERED,
            UiColorRole.BUTTON_SELECTED, UiColorRole.BUTTON_PRESSED, UiColorRole.BUTTON_DISABLED, UiColorRole.ACCENT);

    static MenuScene definition(UiCustomTheme theme, Function<Message, String> text, int page, java.util.function.IntConsumer browse,
                                BiConsumer<Player, UiColorRole> edit, Consumer<Player> back) {
        var items = new java.util.ArrayList<UiNode>();
        for (var role : ROLES) {
            String value = String.format(Locale.ROOT, "#%06X", theme.rgb(role));
            if (role != UiColorRole.ACCENT) value += " · " + percentage(theme.alpha(role)) + "%";
            Component label = Component.text(text.apply(label(role)), theme.color(UiColorRole.TEXT))
                    .append(Component.newline()).append(Component.text(value));
            items.add(Ui.button(label).key("color:" + role.id()).summaryLines(2).tooltip(label)
                    .primary((player,_) -> edit.accept(player,role)).build());
        }
        var colors = Ui.pagedList().key("colors").items(items,LinearSlot.stretch()).page(page,browse).build();
        var content = UiTemplates.reading(colors.key(),
                Ui.heading(Component.text(text.apply(Message.INTERFACE_THEME_EDIT),theme.color(UiColorRole.TEXT)))
                        .key("heading").singleLine().build(),colors);
        return MenuScene.screen("interface-theme-editor").stableAnchor().content(UiTemplates.sheet(
                UiTemplates.window("theme",content),UiTemplates.toolbar("tools",Ui.button(Component.text(text.apply(Message.BACK),theme.color(UiColorRole.TEXT)))
                        .key("back").primary((player,_) -> back.accept(player)).build()))).build();
    }

    static MenuInputForm input(UiCustomTheme theme, UiColorRole role, Function<Message, String> text,
                               BiConsumer<Player, UiColor.Literal> save) {
        var form = MenuInputForm.builder(Component.text(text.apply(label(role))))
                .field(MenuInputForm.Field.text("color", Component.text(text.apply(Message.INTERFACE_THEME_COLOR)),
                        String.format(Locale.ROOT, "#%06X", theme.rgb(role)), 7));
        if (role != UiColorRole.ACCENT)
            form.field(MenuInputForm.Field.text("opacity", Component.text(text.apply(Message.INTERFACE_THEME_OPACITY)), percentage(theme.alpha(role)), 24));
        return form.validate((_, values) -> {
                    try { parse(values.text("color"), role == UiColorRole.ACCENT ? "100" : values.text("opacity")); return null; }
                    catch (IllegalArgumentException error) { return Component.text(text.apply(Message.INTERFACE_THEME_INVALID), theme.color(UiColorRole.DANGER)); }
                }).cancel(Component.text(text.apply(Message.CANCEL)))
                .submit(Component.text(text.apply(Message.INTERFACE_THEME_APPLY)), (player, values) ->
                        save.accept(player, parse(values.text("color"), role == UiColorRole.ACCENT ? "100" : values.text("opacity"))))
                .build();
    }

    static UiColor.Literal parse(String color, String opacity) {
        String hex = color.strip();
        if (!hex.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB");
        String percent = opacity.strip();
        if (percent.endsWith("%")) percent = percent.substring(0, percent.length() - 1).strip();
        return new UiColor.Literal(Integer.parseInt(hex.substring(1), 16), Double.parseDouble(percent) / 100);
    }

    private static String percentage(double alpha) {
        return java.math.BigDecimal.valueOf(alpha).multiply(java.math.BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString();
    }

    private static Message label(UiColorRole role) {
        return switch (role) {
            case WINDOW -> Message.INTERFACE_THEME_ROLE_WINDOW;
            case SIDEBAR -> Message.INTERFACE_THEME_ROLE_SIDEBAR;
            case TOOLBAR -> Message.INTERFACE_THEME_ROLE_TOOLBAR;
            case INPUT -> Message.INTERFACE_THEME_ROLE_INPUT;
            case TOOLTIP -> Message.INTERFACE_THEME_ROLE_TOOLTIP;
            case BUTTON -> Message.INTERFACE_THEME_ROLE_BUTTON;
            case BUTTON_HOVERED -> Message.INTERFACE_THEME_ROLE_BUTTON_HOVERED;
            case BUTTON_SELECTED -> Message.INTERFACE_THEME_ROLE_BUTTON_SELECTED;
            case BUTTON_PRESSED -> Message.INTERFACE_THEME_ROLE_BUTTON_PRESSED;
            case BUTTON_DISABLED -> Message.INTERFACE_THEME_ROLE_BUTTON_DISABLED;
            case ACCENT -> Message.INTERFACE_THEME_ROLE_ACCENT;
            default -> throw new IllegalArgumentException("Not a menu material: " + role);
        };
    }

    private FloatingMenuThemeEditor() { }
}
