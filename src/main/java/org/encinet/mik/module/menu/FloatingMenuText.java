package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
}
