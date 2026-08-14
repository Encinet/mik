package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatStyle;

final class ChatSemanticStyles {
    static final ChatStyle MENTION = style(0xFFD166, false, false);
    static final ChatStyle BROADCAST_MENTION = style(0xFFAA00, true, false);
    static final ChatStyle ITEM = style(0x8BD450, false, false);
    static final ChatStyle EMPTY_ITEM = new ChatStyle(
            0xAAAAAA, null, false, true, false, false);

    private ChatSemanticStyles() {
    }

    private static ChatStyle style(int color, boolean bold, boolean underlined) {
        return new ChatStyle(color, null, bold, false, underlined, false);
    }
}
