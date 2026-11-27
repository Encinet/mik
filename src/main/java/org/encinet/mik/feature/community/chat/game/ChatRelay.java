package org.encinet.mik.feature.community.chat.game;

import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatPresence;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** Publishes authorized shared copies; persistence does not imply client delivery. */
public interface ChatRelay {
    enum Outcome { SAVED, NOT_SUBMITTED, UNCONFIRMED }

    /** The authorization check must also be safe on the worker that sends the publication. */
    CompletionStage<Outcome> publish(ChatMessage message, Optional<Set<UUID>> visibility,
            boolean requireRemoteRecipient, BooleanSupplier authorized);

    void publishPresence(ChatPresence notice, Optional<Set<UUID>> visibility);
}
