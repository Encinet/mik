package org.encinet.mik.module.social.chat;

import org.encinet.mik.module.chat.model.ChatMessage;

/** Public Minecraft chat output port implemented by the social platform host. */
@FunctionalInterface
public interface SocialChatPublisher {
    SocialChatPublishReport publish(ChatMessage message);
}
