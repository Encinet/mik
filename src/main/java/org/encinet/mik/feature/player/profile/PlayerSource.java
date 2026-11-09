package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.encinet.mik.shared.util.TextBadge;

import java.util.Objects;

/** A localized source label, independent of account name tags and channel permissions. */
public record PlayerSource(String label, TextColor color) {
    public static final PlayerSource NONE = new PlayerSource("");

    public PlayerSource {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(color, "color");
    }

    public PlayerSource(String label) { this(label, NamedTextColor.AQUA); }

    Component before(Component identity) {
        return label.isBlank() ? identity : Component.text()
                .append(TextBadge.prefix(label, color)).append(identity).build();
    }

    Component after(Component identity) {
        return label.isBlank() ? identity : Component.text()
                .append(identity).append(TextBadge.suffix(label, color)).build();
    }
}
