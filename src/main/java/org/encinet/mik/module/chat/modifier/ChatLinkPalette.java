package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatStyle;

/** Brand-aware gradients for semantic chat links. */
enum ChatLinkPalette {
    GENERIC(0x4EA5FF, 0x79C0FF),
    GITHUB(0x58A6FF, 0xA371F7),
    MINECRAFT_WIKI(0x55FF55, 0xFFAA00),
    BILIBILI(0x00AEEC, 0xFB7299),
    MOJIRA(0xFF5555, 0xFFAA00),
    MATRIX(0xFFFFFF, 0xAAAAAA),
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
