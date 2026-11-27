package org.encinet.mik.feature.community.chat.parse;

import org.encinet.mik.feature.community.chat.model.ChatStyle;

final class ChatSemanticStyles {
    static final ChatStyle ITEM = style(0x8BD450, false, false);
    static final ChatStyle EMPTY_ITEM = new ChatStyle(
            0xAAAAAA, null, false, true, false, false);

    private ChatSemanticStyles() {
    }

    private static ChatStyle style(int color, boolean bold, boolean underlined) {
        return new ChatStyle(color, null, bold, false, underlined, false);
    }
}
