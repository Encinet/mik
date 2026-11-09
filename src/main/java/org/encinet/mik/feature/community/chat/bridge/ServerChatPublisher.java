package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatServerMessage;

import java.util.UUID;
import java.util.function.Predicate;

/** Publishes trusted server output through conversation and subject-visibility checks. */
@FunctionalInterface
public interface ServerChatPublisher {
    /** The additional visibility restriction runs on the primary thread. */
    void publish(ChatServerMessage message, Predicate<UUID> visibility);

    default void publish(ChatServerMessage message) {
        publish(message, _ -> true);
    }
}
