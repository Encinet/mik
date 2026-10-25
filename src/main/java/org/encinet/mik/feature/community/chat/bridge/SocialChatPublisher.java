package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatMessage;

/** Public Minecraft chat output port implemented by the social platform host. */
@FunctionalInterface
public interface SocialChatPublisher {
    SocialChatPublishReport publish(ChatMessage message);
}
