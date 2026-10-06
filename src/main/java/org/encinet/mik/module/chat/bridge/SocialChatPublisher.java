package org.encinet.mik.module.chat.bridge;

import org.encinet.mik.module.chat.model.ChatMessage;

/** Public Minecraft chat output port implemented by the social platform host. */
@FunctionalInterface
public interface SocialChatPublisher {
    SocialChatPublishReport publish(ChatMessage message);
}
