package org.encinet.mik.module.chat.modifier;

public interface ChatModifier {
    ChatReplacement find(String text, int fromIndex, ChatModifierContext context);
}
