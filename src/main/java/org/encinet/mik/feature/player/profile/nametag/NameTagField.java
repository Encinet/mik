package org.encinet.mik.feature.player.profile.nametag;

import org.encinet.mik.feature.player.profile.PlayerNameTag;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;

enum NameTagField {
    PREFIX, SUFFIX, USERNAME_COLOR;

    String value(PlayerNameTag tag) {
        return switch (this) {
            case PREFIX -> tag.prefix();
            case SUFFIX -> tag.suffix();
            case USERNAME_COLOR -> tag.usernameColorValue();
        };
    }

    PlayerNameTag replace(PlayerNameTag tag, String value) {
        return switch (this) {
            case PREFIX -> new PlayerNameTag(value, tag.suffix(), tag.usernameColor(), tag.rainbowUsername());
            case SUFFIX -> new PlayerNameTag(tag.prefix(), value, tag.usernameColor(), tag.rainbowUsername());
            case USERNAME_COLOR -> PlayerNameTagRenderer.parseUsernameStyle(value)
                    .map(style -> new PlayerNameTag(tag.prefix(), tag.suffix(),
                            style.color(), style.rainbow()))
                    .orElse(tag);
        };
    }
}
