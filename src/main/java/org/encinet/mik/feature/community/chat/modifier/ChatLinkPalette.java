package org.encinet.mik.feature.community.chat.modifier;

import org.encinet.mik.feature.community.chat.model.ChatStyle;

/** Gradients for links recognized by game input. */
enum ChatLinkPalette {
    GENERIC(0x4EA5FF, 0x79C0FF),
    EMAIL(0x55FFFF, 0x5555FF);

    private final ChatStyle style;

    ChatLinkPalette(int startColor, int endColor) {
        style = new ChatStyle(startColor, endColor,
                false, false, false, false);
    }

    ChatStyle style() {
        return style;
    }
}
