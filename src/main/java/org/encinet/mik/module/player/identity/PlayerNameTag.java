package org.encinet.mik.module.player.identity;

/** Raw LuckPerms name decorations, normalized so renderers never handle nulls. */
public record PlayerNameTag(String prefix, String suffix) {

    private static final PlayerNameTag EMPTY = new PlayerNameTag("", "");

    public PlayerNameTag {
        prefix = prefix == null ? "" : prefix;
        suffix = suffix == null ? "" : suffix;
    }

    public static PlayerNameTag empty() {
        return EMPTY;
    }
}
