package org.encinet.mik.shared.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** Plain bracketed display text; labels never become formatting or permission input. */
public final class TextBadge {
    private TextBadge() { }

    public static Component prefix(String label, TextColor color) {
        return render(label, color).append(Component.space());
    }

    public static Component suffix(String label, TextColor color) {
        return Component.space().append(render(label, color));
    }

    private static Component render(String label, TextColor color) {
        return Component.text("[" + java.util.Objects.requireNonNull(label, "label") + "]",
                java.util.Objects.requireNonNull(color, "color"));
    }
}
