package org.encinet.mik.module.chat.modifier;

import net.kyori.adventure.text.Component;

public record ChatReplacement(int start, int end, Component component, ChatReplacementSpacing spacing) {
    public ChatReplacement(int start, int end, Component component) {
        this(start, end, component, ChatReplacementSpacing.PRESERVE);
    }

    public static ChatReplacement padded(int start, int end, Component component) {
        return new ChatReplacement(start, end, component, ChatReplacementSpacing.PAD_WHEN_JOINED);
    }
}
