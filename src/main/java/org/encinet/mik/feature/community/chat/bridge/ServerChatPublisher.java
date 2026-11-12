package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatServerMessage;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Publishes trusted server output through conversation and subject-visibility checks. */
@FunctionalInterface
public interface ServerChatPublisher {
    /** Online checks run on the primary thread; offline visibility is an explicit publication rule. */
    record Visibility(Predicate<UUID> includes, boolean includesOffline) {
        public Visibility { Objects.requireNonNull(includes, "includes"); }
    }

    void publish(ChatServerMessage message, Visibility visibility);

    default void publish(ChatServerMessage message) {
        publish(message, new Visibility(_ -> true, true));
    }
}
