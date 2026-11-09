package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatDelivery;

/** Output port for accepted messages; transport authority remains with its implementation. */
@FunctionalInterface
public interface ChatMessagePublisher {
    void publish(ChatDelivery delivery);
}
