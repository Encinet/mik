package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import java.util.Objects;

/** Effective LuckPerms decorations and username color, captured as one identity snapshot. */
public record PlayerNameTag(String prefix, String suffix, TextColor usernameColor) {

    private static final PlayerNameTag EMPTY = new PlayerNameTag("", "");

    public PlayerNameTag {
        prefix = prefix == null ? "" : prefix;
        suffix = suffix == null ? "" : suffix;
        usernameColor = Objects.requireNonNullElse(usernameColor, NamedTextColor.WHITE);
    }

    /** Portable prefix/suffix styles do not carry a role's username color. */
    public PlayerNameTag(String prefix, String suffix) {
        this(prefix, suffix, NamedTextColor.WHITE);
    }

    public static PlayerNameTag empty() {
        return EMPTY;
    }
}
