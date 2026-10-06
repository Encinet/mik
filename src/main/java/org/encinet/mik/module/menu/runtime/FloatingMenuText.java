package org.encinet.mik.module.menu.runtime;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;

/** Shared text-style boundary for every floating-menu presentation. */
final class FloatingMenuText {

    private FloatingMenuText() {
    }

    /** Makes the client payload explicit without replacing intentional colors. */
    static Component withDefaultWhite(Component component) {
        return Objects.requireNonNull(component, "component")
                .colorIfAbsent(NamedTextColor.WHITE);
    }

    /** Lifts low-contrast menu colors on dark world surfaces, preserving their hue. */
    static Component readableOnDark(Component component) {
        return brighten(Objects.requireNonNull(component, "component"))
                .colorIfAbsent(NamedTextColor.WHITE);
    }

    private static Component brighten(Component component) {
        Component result = component;
        if (NamedTextColor.DARK_GRAY.equals(result.color())) {
            result = result.color(NamedTextColor.GRAY);
        } else if (NamedTextColor.DARK_PURPLE.equals(result.color())) {
            result = result.color(NamedTextColor.LIGHT_PURPLE);
        } else if (NamedTextColor.RED.equals(result.color())) {
            result = result.color(TextColor.color(0xFFAAAA));
        } else if (NamedTextColor.BLUE.equals(result.color())) {
            result = result.color(TextColor.color(0xBBBBFF));
        } else if (NamedTextColor.DARK_AQUA.equals(result.color())) {
            result = result.color(TextColor.color(0x77DDDD));
        } else if (NamedTextColor.DARK_GREEN.equals(result.color())) {
            result = result.color(TextColor.color(0x88DD88));
        } else if (NamedTextColor.DARK_BLUE.equals(result.color())) {
            result = result.color(TextColor.color(0xBBBBFF));
        } else if (NamedTextColor.DARK_RED.equals(result.color())) {
            result = result.color(TextColor.color(0xFFAAAA));
        }
        if (!result.children().isEmpty()) {
            result = result.children(result.children().stream()
                    .map(FloatingMenuText::brighten).toList());
        }
        return result;
    }
}
