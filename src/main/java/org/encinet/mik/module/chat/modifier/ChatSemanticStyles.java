package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatStyle;

final class ChatSemanticStyles {
    static final ChatStyle LINK = style(0x4EA5FF, false, true);
    static final ChatStyle BILIBILI = style(0xFF6FAE, false, true);
    static final ChatStyle GITHUB = style(0xC9D1D9, false, true);
    static final ChatStyle EMAIL = style(0x55FFFF, false, true);
    static final ChatStyle MATRIX = style(0x0DBD8B, false, true);
    static final ChatStyle MENTION = style(0xFFD166, false, false);
    static final ChatStyle BROADCAST_MENTION = style(0xFFAA00, true, false);
    static final ChatStyle ITEM = style(0x8BD450, false, false);
    static final ChatStyle EMPTY_ITEM = new ChatStyle(
            0xAAAAAA, false, true, false, false);

    private ChatSemanticStyles() {
    }

    private static ChatStyle style(int color, boolean bold, boolean underlined) {
        return new ChatStyle(color, bold, false, underlined, false);
    }
}
