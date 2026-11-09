package org.encinet.mik.feature.player.profile.nametag;

import org.encinet.mik.feature.player.profile.PlayerNameTag;

enum NameTagField {
    PREFIX, SUFFIX;

    String value(PlayerNameTag tag) { return this == PREFIX ? tag.prefix() : tag.suffix(); }

    PlayerNameTag replace(PlayerNameTag tag, String value) {
        return this == PREFIX ? new PlayerNameTag(value, tag.suffix(), tag.usernameColor())
                : new PlayerNameTag(tag.prefix(), value, tag.usernameColor());
    }
}
